package com.bongj9.briefing.live;

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
    private final TossClient toss;

    LiveController(LiveBroadcaster broadcaster, TossClient toss) {
        this.broadcaster = broadcaster;
        this.toss = toss;
    }

    // 실시간 체결(trade)·큰 변동(move) 스트림
    @GetMapping("/api/stream")
    SseEmitter stream() {
        return broadcaster.connect();
    }

    // 차트 초기 데이터: 최근 1분봉 200개 (오래된 순, 시각은 epoch 초)
    @GetMapping("/api/candles")
    List<Bar> candles(@RequestParam String symbol) {
        return toss.minuteCandles(symbol, 200).stream()
                .map(c -> new Bar(OffsetDateTime.parse(c.timestamp()).toEpochSecond(),
                        new BigDecimal(c.openPrice()), new BigDecimal(c.highPrice()),
                        new BigDecimal(c.lowPrice()), new BigDecimal(c.closePrice())))
                .sorted(Comparator.comparingLong(Bar::time))
                .toList();
    }

    record Bar(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
    }
}
