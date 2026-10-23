package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.LiveEvents;
import com.bongj9.briefing.application.port.TickRepository;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Quote;
import com.bongj9.briefing.domain.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

// 실시간 시세가 들어왔을 때의 흐름: 시세 계산 → 저장 → 화면 전송 → 급등락 감지
@Service
public class LiveFeed {

    private static final Logger log = LoggerFactory.getLogger(LiveFeed.class);
    // 체결이 이만큼 끊기면 다른 세션(전날 등)으로 본다
    // ponytail: 미국 종목은 토스 주간·프리·정규·애프터 거래가 거의 이어져 하루 넘게 한 세션으로 묶일 수 있다
    private static final Duration SESSION_GAP = Duration.ofHours(1);

    private final QuoteService quoteService;
    private final PriceMoveDetector detector;
    private final TickRepository ticks;
    private final LiveEvents events;

    public LiveFeed(QuoteService quoteService, PriceMoveDetector detector, TickRepository ticks, LiveEvents events) {
        this.quoteService = quoteService;
        this.detector = detector;
        this.ticks = ticks;
        this.events = events;
    }

    public void onTrade(String symbol, BigDecimal price, String currency, String timestamp, BigDecimal volume) {
        Quote quote = quoteService.toQuote(symbol, price, currency, timestamp, volume);
        ticks.add(quote);
        events.send("trade", quote);
        detector.check(quote).ifPresent(move -> {
            log.info("큰 변동 감지 [{}]: {}%", move.getStockCode(), move.getChangeRate());
            events.send("move", move);
        });
    }

    public void onOrderbook(String symbol, Orderbook book) {
        events.send("orderbook", new BookEvent(symbol, book));
    }

    // 틱 차트 초기 데이터: 저장된 최근 체결 중 마지막 세션만 (오래된 순)
    // 틱 차트는 x축이 순번이라 전날 체결이 섞이면 밤사이 공백 없이 오늘 체결에 붙어 버린다
    public List<Quote> recentTicks(String symbol) {
        return latestSession(ticks.recent(symbol)).stream()
                .map(t -> quoteService.toQuote(symbol, t.price(), t.currency(), t.timestamp(), t.volume()))
                .toList();
    }

    static List<Trade> latestSession(List<Trade> oldestFirst) {
        for (int i = oldestFirst.size() - 1; i > 0; i--) {
            OffsetDateTime prev = OffsetDateTime.parse(oldestFirst.get(i - 1).timestamp());
            OffsetDateTime cur = OffsetDateTime.parse(oldestFirst.get(i).timestamp());
            if (Duration.between(prev, cur).compareTo(SESSION_GAP) > 0) {
                return oldestFirst.subList(i, oldestFirst.size());
            }
        }
        return oldestFirst;
    }

    public record BookEvent(String stockCode, Orderbook book) {
    }
}
