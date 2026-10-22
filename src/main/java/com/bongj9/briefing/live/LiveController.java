package com.bongj9.briefing.live;

import com.bongj9.briefing.quote.Quote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
class LiveController {

    private final LiveBroadcaster broadcaster;
    private final TickBuffer ticks;

    LiveController(LiveBroadcaster broadcaster, TickBuffer ticks) {
        this.broadcaster = broadcaster;
        this.ticks = ticks;
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
}
