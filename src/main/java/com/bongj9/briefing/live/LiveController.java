package com.bongj9.briefing.live;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class LiveController {

    private final LiveBroadcaster broadcaster;

    LiveController(LiveBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    // 실시간 체결(trade)·큰 변동(move) 스트림
    @GetMapping("/api/stream")
    SseEmitter stream() {
        return broadcaster.connect();
    }
}
