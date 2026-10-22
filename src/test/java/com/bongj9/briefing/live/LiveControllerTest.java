package com.bongj9.briefing.live;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveControllerTest {

    @Test
    void 같은_구간의_봉을_시고저종_거래량으로_합친다() {
        List<LiveController.Bar> merged = LiveController.merge(List.of(
                bar(120, "11", "13", "10", "12", "5"),
                bar(0, "10", "12", "9", "11", "1"),
                bar(60, "11", "14", "11", "13", "2"),
                bar(300, "12", "12", "12", "12", "7")), t -> t - t % 300);

        assertThat(merged).hasSize(2);
        LiveController.Bar first = merged.get(0);
        assertThat(first.time()).isZero();
        assertThat(first.open()).isEqualByComparingTo("10");
        assertThat(first.high()).isEqualByComparingTo("14");
        assertThat(first.low()).isEqualByComparingTo("9");
        assertThat(first.close()).isEqualByComparingTo("12");
        assertThat(first.volume()).isEqualByComparingTo("8");
        assertThat(merged.get(1).time()).isEqualTo(300);
    }

    private static LiveController.Bar bar(long time, String o, String h, String l, String c, String v) {
        return new LiveController.Bar(time, new BigDecimal(o), new BigDecimal(h), new BigDecimal(l), new BigDecimal(c), new BigDecimal(v));
    }
}
