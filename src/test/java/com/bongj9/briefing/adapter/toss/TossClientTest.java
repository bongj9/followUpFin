package com.bongj9.briefing.adapter.toss;

import com.bongj9.briefing.adapter.toss.TossClient.TossCandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TossClientTest {

    @Test
    void 문자열_봉을_숫자로_바꾸고_비어_있는_봉은_버린다() {
        var candle = TossCandle.toCandleOrNull(
                new TossCandle("2026-10-22T09:00:00+09:00", "1", "3", "0.5", "2.25", "100"));
        assertThat(candle.close()).isEqualByComparingTo("2.25");
        assertThat(candle.time()).hasToString("2026-10-22T00:00:00Z");

        assertThat(TossCandle.toCandleOrNull(null)).isNull();
        assertThat(TossCandle.toCandleOrNull(
                new TossCandle("2026-10-23T09:00:00+09:00", "1", "1", "1", "1", null))).isNull();
    }
}
