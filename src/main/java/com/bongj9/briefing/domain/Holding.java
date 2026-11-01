package com.bongj9.briefing.domain;

import java.math.BigDecimal;

// 증권 계좌의 보유 종목 한 개. 가격·금액은 거래 통화(currency) 기준, 수익률은 % (-61.86)
// 현재가·평가금액·손익은 증권사가 계산한 값 그대로 (없으면 null)
public record Holding(String symbol, String name, BigDecimal quantity, BigDecimal averagePrice, String currency,
                      BigDecimal lastPrice, BigDecimal purchaseAmount, BigDecimal marketValue,
                      BigDecimal profitLoss, BigDecimal profitRate, BigDecimal dailyProfitLoss, BigDecimal dailyProfitRate) {
}
