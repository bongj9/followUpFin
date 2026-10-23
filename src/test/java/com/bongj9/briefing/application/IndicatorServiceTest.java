package com.bongj9.briefing.application;

import com.bongj9.briefing.domain.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorServiceTest {

    @Test
    void 마지막_1분봉을_현재값으로_전일_종가_대비_등락률과_오래된_순_그래프를_만든다() {
        List<Candle> minutes = List.of(candle("2026-10-23T05:02:00Z", "2830"), candle("2026-10-23T05:01:00Z", "2820")); // 최신순으로 옴
        List<Candle> days = List.of(candle("2026-10-23T06:30:00Z", "2830"), candle("2026-10-22T06:30:00Z", "2800"));

        IndicatorService.Indicator kospi = IndicatorService.toIndicator("코스피", minutes, days);

        assertThat(kospi.last()).isEqualByComparingTo("2830");
        assertThat(kospi.changeRate()).isEqualByComparingTo("1.07"); // (2830 - 2800) / 2800
        assertThat(kospi.spark()).containsExactly(new BigDecimal("2820"), new BigDecimal("2830"));
    }

    @Test
    void 봉이_없으면_값_없이_돌려준다() {
        IndicatorService.Indicator empty = IndicatorService.toIndicator("코스닥", List.of(), List.of());
        assertThat(empty.last()).isNull();
        assertThat(empty.changeRate()).isNull();
    }

    private static Candle candle(String time, String close) {
        BigDecimal c = new BigDecimal(close);
        return new Candle(Instant.parse(time), c, c, c, c, BigDecimal.ONE);
    }
}
