package com.bongj9.briefing.domain;

import java.math.BigDecimal;

// 증권 계좌의 보유 종목 한 개. 가격은 거래 통화(currency) 기준
public record Holding(String symbol, String name, BigDecimal quantity, BigDecimal averagePrice, String currency) {
}
