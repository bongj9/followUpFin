package com.bongj9.briefing.adapter.toss;

import com.bongj9.briefing.adapter.toss.TossClient.TossOrderbook;
import com.bongj9.briefing.application.LiveFeed;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.FollowedStocksChanged;
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

// 토스증권 실시간 체결·호가 웹소켓. 서버가 연결 하나를 유지하고 받은 걸 LiveFeed에 넘긴다 (저장·SSE 전송·급등락 감지)
// 브라우저가 직접 붙을 수 없는 이유: 토큰이 비밀이고 토스가 등록된 IP만 받는다
@Component
@ConditionalOnProperty(name = "briefing.scheduling", havingValue = "true", matchIfMissing = true)
class TossStream {

    private static final Logger log = LoggerFactory.getLogger(TossStream.class);
    private static final URI ENDPOINT = URI.create("wss://openapi-ws.tossinvest.com/ws/v1");
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(1);

    private final TossClient toss;
    private final LiveFeed feed;
    private final FollowedStockRepository stockRepository;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    private volatile WebSocket socket;
    private volatile boolean connecting;
    private Duration backoff = Duration.ofSeconds(1);
    private Instant nextAttempt = Instant.EPOCH;

    TossStream(TossClient toss, LiveFeed feed, FollowedStockRepository stockRepository) {
        this.toss = toss;
        this.feed = feed;
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

    // 구독은 full-replace: 보낼 때마다 이 배열이 현재 구독 전체가 된다. 종목마다 체결 + 호가
    // 연결당 100건 한도: 체결(급등락 감지에 필요)을 먼저 채우고 남는 자리만 호가에 쓴다
    // ponytail: 51종목부터는 뒤쪽 종목의 호가가 안 온다. 그때는 화면에서 보고 있는 종목만 호가를 구독할 것
    private static final int MAX_TOPICS = 100;

    private void subscribe() {
        WebSocket ws = socket;
        if (ws == null) {
            return;
        }
        Map<Boolean, List<String>> byMarket = stockRepository.findAll().stream()
                .collect(Collectors.partitioningBy(FollowedStock::isKorean,
                        Collectors.mapping(FollowedStock::getStockCode, Collectors.toList())));
        ws.sendText(json.writeValueAsString(declaration(byMarket)), true);
    }

    // byMarket: true = 국내 종목, false = 미국 종목
    static List<Map<String, Object>> declaration(Map<Boolean, List<String>> byMarket) {
        List<Map<String, Object>> declaration = new ArrayList<>();
        int room = MAX_TOPICS;
        for (String channel : List.of("trade", "orderbook")) {
            for (boolean korean : List.of(false, true)) {
                List<String> codes = byMarket.get(korean);
                codes = codes.subList(0, Math.min(codes.size(), room));
                room -= codes.size();
                if (!codes.isEmpty()) {
                    declaration.add(Map.of("type", channel + (korean ? ":kr" : ":us"), "codes", codes));
                }
            }
        }
        return declaration;
    }

    void handle(String text) {
        JsonNode frame = json.readTree(text);
        switch (frame.path("type").asString()) {
            case "message" -> onMessage(frame);
            case "subscriptions" -> {
                log.info("실시간 구독: {}", frame.path("subscribed"));
                if (!frame.path("rejected").isEmpty()) {
                    log.error("실시간 구독 거부: {}", frame.path("rejected"));
                }
            }
            case "error" -> log.warn("토스 실시간 오류: {}", frame.path("error"));
            default -> { } // pong
        }
    }

    private void onMessage(JsonNode frame) {
        String topic = frame.path("topic").asString(); // trade:us:AAPL, orderbook:us:AAPL
        String symbol = topic.substring(topic.lastIndexOf(':') + 1);
        JsonNode data = frame.path("data");
        if (topic.startsWith("orderbook:")) {
            feed.onOrderbook(symbol, json.treeToValue(data, TossOrderbook.class).toOrderbook());
            return;
        }
        if (!topic.startsWith("trade:")) {
            return;
        }
        feed.onTrade(symbol, new BigDecimal(data.path("price").asString()), data.path("currency").asString(),
                data.path("timestamp").asString(), new BigDecimal(data.path("volume").asString("0")));
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
