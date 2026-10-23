package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.LiveEvents;
import com.bongj9.briefing.application.port.TickRepository;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

// 실시간 시세가 들어왔을 때의 흐름: 시세 계산 → 저장 → 화면 전송 → 급등락 감지
@Service
public class LiveFeed {

    private static final Logger log = LoggerFactory.getLogger(LiveFeed.class);

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

    // 틱 차트 초기 데이터: 저장된 최근 체결 (오래된 순)
    public List<Quote> recentTicks(String symbol) {
        return ticks.recent(symbol).stream()
                .map(t -> quoteService.toQuote(symbol, t.price(), t.currency(), t.timestamp(), t.volume()))
                .toList();
    }

    public record BookEvent(String stockCode, Orderbook book) {
    }
}
