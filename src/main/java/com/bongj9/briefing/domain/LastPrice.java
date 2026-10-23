package com.bongj9.briefing.domain;

import java.math.BigDecimal;

// 현재가 (전일 대비는 QuoteService가 계산)
public record LastPrice(String symbol, BigDecimal price, String currency, String timestamp) {
}
