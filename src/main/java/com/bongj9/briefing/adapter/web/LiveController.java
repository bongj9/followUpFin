package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.CandleService;
import com.bongj9.briefing.application.LiveFeed;
import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Quote;
import com.bongj9.briefing.domain.Trade;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
class LiveController {

    private final LiveBroadcaster broadcaster;
    private final LiveFeed feed;
    private final CandleService candles;
    private final MarketData market;

    LiveController(LiveBroadcaster broadcaster, LiveFeed feed, CandleService candles, MarketData market) {
        this.broadcaster = broadcaster;
        this.feed = feed;
        this.candles = candles;
        this.market = market;
    }

    // 실시간 체결(trade)·호가(orderbook)·큰 변동(move) 스트림
    @GetMapping("/api/stream")
    SseEmitter stream() {
        return broadcaster.connect();
    }

    // 틱 차트 초기 데이터: DB에 쌓인 최근 체결 (최대 1000건)
    @GetMapping("/api/ticks")
    List<Quote> ticks(@RequestParam String symbol) {
        return feed.recentTicks(symbol.toUpperCase());
    }

    // 호가창 초기 데이터. 이후는 SSE orderbook 이벤트로 갱신
    @GetMapping("/api/orderbook")
    Orderbook orderbook(@RequestParam String symbol) {
        requireMarket();
        return market.orderbook(symbol.toUpperCase());
    }

    // 체결창 초기 데이터 (당일 최근 50건, 최신순). 이후는 SSE trade 이벤트로 갱신
    @GetMapping("/api/trades")
    List<Trade> trades(@RequestParam String symbol) {
        requireMarket();
        return market.trades(symbol.toUpperCase());
    }

    // 캔들 차트 초기 데이터 (오래된 순)
    @GetMapping("/api/candles")
    List<CandleService.Bar> candles(@RequestParam String symbol, @RequestParam(defaultValue = "1m") String interval) {
        if (!candles.supports(interval)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 주기: " + interval);
        }
        return candles.bars(symbol.toUpperCase(), interval);
    }

    private void requireMarket() {
        if (!market.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "secret.yml에 토스 키가 없습니다");
        }
    }
}
