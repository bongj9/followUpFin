package com.bongj9.briefing.application;

import com.bongj9.briefing.domain.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveFeedTest {

    @Test
    void 틱_차트에는_마지막_세션의_체결만_보낸다() {
        List<Trade> ticks = List.of(
                trade("2026-10-22T19:59:58+09:00"), // 전날 NXT 마감 직전
                trade("2026-10-22T19:59:59+09:00"),
                trade("2026-10-23T08:00:01+09:00"), // 밤사이 12시간 공백 → 새 세션
                trade("2026-10-23T08:40:00+09:00"),
                trade("2026-10-23T09:00:00+09:00")); // 장 사이 20분 공백은 같은 세션

        assertThat(LiveFeed.latestSession(ticks)).extracting(Trade::timestamp)
                .containsExactly("2026-10-23T08:00:01+09:00", "2026-10-23T08:40:00+09:00", "2026-10-23T09:00:00+09:00");
        assertThat(LiveFeed.latestSession(ticks.subList(2, 5))).hasSize(3);
        assertThat(LiveFeed.latestSession(List.of())).isEmpty();
    }

    private static Trade trade(String timestamp) {
        return new Trade(BigDecimal.ONE, BigDecimal.ONE, timestamp, "KRW");
    }
}
