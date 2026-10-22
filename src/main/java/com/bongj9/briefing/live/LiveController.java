package com.bongj9.briefing.live;

import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.TossClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@RestController
class LiveController {

    private final LiveBroadcaster broadcaster;
    private final TickBuffer ticks;
    private final TossClient toss;

    LiveController(LiveBroadcaster broadcaster, TickBuffer ticks, TossClient toss) {
        this.broadcaster = broadcaster;
        this.ticks = ticks;
        this.toss = toss;
    }

    // 실시간 체결(trade)·큰 변동(move) 스트림
    @GetMapping("/api/stream")
    SseEmitter stream() {
        return broadcaster.connect();
    }

    // 틱 차트 초기 데이터: 서버가 켜진 뒤 받은 최근 체결 (최대 1000건)
    @GetMapping("/api/ticks")
    List<Quote> ticks(@RequestParam String symbol) {
        return ticks.recent(symbol.toUpperCase());
    }

    // 캔들 차트 초기 데이터: 토스 최근 1분봉 200개 (오래된 순, 시각은 epoch 초)
    // 토스 분봉 시각은 봉이 끝나는 시각(21:50~21:51 봉 = 21:51)이라 차트가 쓰는 시작 시각으로 1분 당긴다
    @GetMapping("/api/candles")
    List<Bar> candles(@RequestParam String symbol) {
        return toss.minuteCandles(symbol.toUpperCase(), 200).stream()
                .map(c -> new Bar(OffsetDateTime.parse(c.timestamp()).toEpochSecond() - 60,
                        new BigDecimal(c.openPrice()), new BigDecimal(c.highPrice()),
                        new BigDecimal(c.lowPrice()), new BigDecimal(c.closePrice())))
                .sorted(Comparator.comparingLong(Bar::time))
                .toList();
    }

    record Bar(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
    }
}
