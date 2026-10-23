package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.LiveEvents;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// 실시간 시세가 들어왔을 때의 흐름: 시세 계산 → 화면 전송 → 급등락 감지 (체결은 저장하지 않는다)
@Service
public class LiveFeed {

    private static final Logger log = LoggerFactory.getLogger(LiveFeed.class);
    private final QuoteService quoteService;
    private final PriceMoveDetector detector;
    private final LiveEvents events;

    public LiveFeed(QuoteService quoteService, PriceMoveDetector detector, LiveEvents events) {
        this.quoteService = quoteService;
        this.detector = detector;
        this.events = events;
    }

    public void onTrade(String symbol, BigDecimal price, String currency, String timestamp, BigDecimal volume) {
        Quote quote = quoteService.toQuote(symbol, price, currency, timestamp, volume);
        events.send("trade", quote);
        detector.check(quote).ifPresent(move -> {
            log.info("큰 변동 감지 [{}]: {}%", move.getStockCode(), move.getChangeRate());
            events.send("move", move);
        });
    }

    public void onOrderbook(String symbol, Orderbook book) {
        events.send("orderbook", new BookEvent(symbol, book));
    }

    public record BookEvent(String stockCode, Orderbook book) {
    }
}
