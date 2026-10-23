package com.bongj9.briefing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

// change: 전일 종가 대비 등락폭, changeRate: 등락률(%) 소수 둘째 자리. 전일 종가를 모르면 둘 다 null
// volume: 실시간 체결 한 건의 수량 (현재가 조회로 만든 시세는 null)
public record Quote(String stockCode, BigDecimal lastPrice, BigDecimal prevClose, BigDecimal change, BigDecimal changeRate,
                    String currency, String timestamp, boolean bigMove, BigDecimal volume) {

    public static BigDecimal change(BigDecimal last, BigDecimal prevClose) {
        return prevClose == null ? null : last.subtract(prevClose);
    }

    public static BigDecimal changeRate(BigDecimal last, BigDecimal prevClose) {
        if (prevClose == null || prevClose.signum() == 0) {
            return null;
        }
        return last.subtract(prevClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(prevClose, 2, RoundingMode.HALF_UP);
    }

    public static boolean isBigMove(BigDecimal changeRate, BigDecimal threshold) {
        return changeRate != null && changeRate.abs().compareTo(threshold) >= 0;
    }
}
