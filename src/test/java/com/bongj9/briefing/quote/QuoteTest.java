package com.bongj9.briefing.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteTest {

    private static final BigDecimal FIVE = new BigDecimal("5");

    @Test
    void 전일_종가_대비_등락률() {
        assertThat(Quote.changeRate(new BigDecimal("105"), new BigDecimal("100"))).isEqualByComparingTo("5.00");
        assertThat(Quote.changeRate(new BigDecimal("189.25"), new BigDecimal("200.00"))).isEqualByComparingTo("-5.38");
        assertThat(Quote.changeRate(new BigDecimal("72300"), new BigDecimal("71600"))).isEqualByComparingTo("0.98");
    }

    @Test
    void 전일_종가_대비_등락폭() {
        assertThat(Quote.change(new BigDecimal("343.11"), new BigDecimal("343.99"))).isEqualByComparingTo("-0.88");
        assertThat(Quote.change(new BigDecimal("72300"), new BigDecimal("71600"))).isEqualByComparingTo("700");
        assertThat(Quote.change(new BigDecimal("100"), null)).isNull();
    }

    @Test
    void 전일_종가를_모르면_등락률도_없다() {
        assertThat(Quote.changeRate(new BigDecimal("100"), null)).isNull();
        assertThat(Quote.changeRate(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void 위아래_5퍼센트_이상이면_큰_변동() {
        assertThat(Quote.isBigMove(new BigDecimal("5.00"), FIVE)).isTrue();
        assertThat(Quote.isBigMove(new BigDecimal("-5.38"), FIVE)).isTrue();
        assertThat(Quote.isBigMove(new BigDecimal("4.99"), FIVE)).isFalse();
        assertThat(Quote.isBigMove(null, FIVE)).isFalse();
    }
}
