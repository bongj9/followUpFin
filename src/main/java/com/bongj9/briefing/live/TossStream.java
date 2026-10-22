package com.bongj9.briefing.live;

import com.bongj9.briefing.move.PriceMoveDetector;
import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.QuoteService;
import com.bongj9.briefing.quote.TossClient;
import com.bongj9.briefing.stock.FollowedStock;
import com.bongj9.briefing.stock.FollowedStockRepository;
import com.bongj9.briefing.stock.FollowedStocksChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

// 토스증권 실시간 체결 웹소켓. 서버가 연결 하나를 유지하고 받은 체결을 SSE로 화면에 넘긴다
// 브라우저가 직접 붙을 수 없는 이유: 토큰이 비밀이고 토스가 등록된 IP만 받는다
@Component
@ConditionalOnProperty(name = "briefing.scheduling", havingValue = "true", matchIfMissing = true)
class TossStream {

    private static final Logger log = LoggerFactory.getLogger(TossStream.class);
    private static final URI ENDPOINT = URI.create("wss://openapi-ws.tossinvest.com/ws/v1");
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(1);

    private final TossClient toss;
    private final QuoteService quoteService;
    private final PriceMoveDetector detector;
    private final LiveBroadcaster broadcaster;
    private final TickBuffer ticks;
    private final FollowedStockRepository stockRepository;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    private volatile WebSocket socket;
    private volatile boolean connecting;
    private Duration backoff = Duration.ofSeconds(1);
    private Instant nextAttempt = Instant.EPOCH;

    TossStream(TossClient toss, QuoteService quoteService, PriceMoveDetector detector,
               LiveBroadcaster broadcaster, TickBuffer ticks, FollowedStockRepository stockRepository) {
        this.toss = toss;
        this.quoteService = quoteService;
        this.detector = detector;
        this.broadcaster = broadcaster;
        this.ticks = ticks;
        this.stockRepository = stockRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        reconnectIfNeeded();
    }

    // 끊겼으면 다시 연결 (1초부터 두 배씩, 최대 1분 간격)
    @Scheduled(fixedDelay = 5_000)
    synchronized void reconnectIfNeeded() {
        if (!toss.isConfigured() || socket != null || connecting || Instant.now().isBefore(nextAttempt)) {
            return;
        }
        connecting = true;
        http.newWebSocketBuilder()
                .header("Authorization", "Bearer " + toss.token())
                .buildAsync(ENDPOINT, new Listener())
                .whenComplete((ws, e) -> {
                    connecting = false;
                    if (e != null) {
                        onDisconnected("연결 실패: " + rootMessage(e), e.getCause() instanceof WebSocketHandshakeException h
                                && h.getResponse().statusCode() == 401);
                        return;
                    }
                    socket = ws;
                    backoff = Duration.ofSeconds(1);
                    log.info("토스 실시간 시세 연결됨");
                    subscribe();
                });
    }

    // 180초 동안 클라이언트 송신이 없으면 토스가 끊으므로 60초마다 PING
    @Scheduled(fixedRate = 60_000)
    void ping() {
        WebSocket ws = socket;
        if (ws != null) {
            ws.sendText("PING", true);
        }
    }

    @EventListener(FollowedStocksChanged.class)
    void onFollowedStocksChanged() {
        subscribe();
    }

    // 구독은 full-replace: 보낼 때마다 이 배열이 현재 구독 전체가 된다
    private void subscribe() {
        WebSocket ws = socket;
        if (ws == null) {
            return;
        }
        Map<Boolean, List<String>> byMarket = stockRepository.findAll().stream()
                .collect(Collectors.partitioningBy(FollowedStock::isKorean,
                        Collectors.mapping(FollowedStock::getStockCode, Collectors.toList())));
        List<Map<String, Object>> declaration = new ArrayList<>();
        if (!byMarket.get(false).isEmpty()) {
            declaration.add(Map.of("type", "trade:us", "codes", byMarket.get(false)));
        }
        if (!byMarket.get(true).isEmpty()) {
            declaration.add(Map.of("type", "trade:kr", "codes", byMarket.get(true)));
        }
        ws.sendText(json.writeValueAsString(declaration), true);
    }

    void handle(String text) {
        JsonNode frame = json.readTree(text);
        switch (frame.path("type").asString()) {
            case "message" -> onMessage(frame);
            case "subscriptions" -> log.info("실시간 구독: {} / 거부: {}", frame.path("subscribed"), frame.path("rejected"));
            case "error" -> log.warn("토스 실시간 오류: {}", frame.path("error"));
            default -> { } // pong
        }
    }

    private void onMessage(JsonNode frame) {
        String topic = frame.path("topic").asString(); // trade:us:AAPL
        if (!topic.startsWith("trade:")) {
            return;
        }
        String symbol = topic.substring(topic.lastIndexOf(':') + 1);
        JsonNode data = frame.path("data");
        Quote quote = quoteService.toQuote(symbol, new BigDecimal(data.path("price").asString()),
                data.path("currency").asString(), data.path("timestamp").asString());
        ticks.add(quote);
        broadcaster.send("trade", quote);
        detector.check(quote).ifPresent(move -> {
            log.info("큰 변동 감지 [{}]: {}%", move.getStockCode(), move.getChangeRate());
            broadcaster.send("move", move);
        });
    }

    private synchronized void onDisconnected(String reason, boolean tokenRejected) {
        socket = null;
        if (tokenRejected) {
            toss.invalidateToken();
        }
        nextAttempt = Instant.now().plus(backoff);
        log.warn("토스 실시간 시세 끊김 ({}), {}초 뒤 재연결", reason, backoff.toSeconds());
        backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getClass().getSimpleName() + " " + e.getMessage();
    }

    private class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                try {
                    handle(text);
                } catch (RuntimeException e) {
                    log.warn("실시간 메시지 처리 실패: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            onDisconnected("close " + statusCode + " " + reason, false);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            onDisconnected(rootMessage(error), false);
        }
    }
}
