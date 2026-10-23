package com.bongj9.briefing.domain;

import java.math.BigDecimal;
import java.time.Instant;

// time은 봉이 끝나는 시각 (21:50~21:51 1분봉 = 21:51)
public record Candle(Instant time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                     BigDecimal volume) {
}
