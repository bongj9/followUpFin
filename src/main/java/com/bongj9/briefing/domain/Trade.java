package com.bongj9.briefing.domain;

import java.math.BigDecimal;

// 체결 한 건
public record Trade(BigDecimal price, BigDecimal volume, String timestamp, String currency) {
}
