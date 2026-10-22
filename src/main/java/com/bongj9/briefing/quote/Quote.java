package com.bongj9.briefing.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;

// changeRate: 전일 종가 대비 등락률(%), 소수 둘째 자리. 전일 종가를 모르면 null
public record Quote(String stockCode, BigDecimal lastPrice, BigDecimal prevClose, BigDecimal changeRate,
                    String currency, String timestamp, boolean bigMove) {

    static BigDecimal changeRate(BigDecimal last, BigDecimal prevClose) {
        if (prevClose == null || prevClose.signum() == 0) {
            return null;
        }
        return last.subtract(prevClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(prevClose, 2, RoundingMode.HALF_UP);
    }

    static boolean isBigMove(BigDecimal changeRate, BigDecimal threshold) {
        return changeRate != null && changeRate.abs().compareTo(threshold) >= 0;
    }
}
