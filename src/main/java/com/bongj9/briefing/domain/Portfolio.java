package com.bongj9.briefing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// 계좌 전체: 보유 종목과 증권사가 계산한 합계. 금액은 통화(KRW, USD)별, 수익률은 % (-74.8)
public record Portfolio(List<Holding> items, Map<String, BigDecimal> purchaseAmount, Map<String, BigDecimal> marketValue,
                        Map<String, BigDecimal> profitLoss, BigDecimal profitRate,
                        Map<String, BigDecimal> dailyProfitLoss, BigDecimal dailyProfitRate) {

    public static final Portfolio EMPTY = new Portfolio(List.of(), Map.of(), Map.of(), Map.of(), null, Map.of(), null);
}
