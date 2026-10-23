package com.bongj9.briefing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

// 장중 급등락 기록: 최근 몇 분 안에 그 종목 평소보다 훨씬 크게 움직인 순간. 하루에 여러 번 있을 수 있다
@Entity
public class IntradayMove {

    @Id
    @GeneratedValue
    private Long id;
    private String stockCode;
    private BigDecimal fromPrice; // 창 안의 저점(급등) 또는 고점(급락)
    private BigDecimal lastPrice;
    private BigDecimal changeRate; // fromPrice 대비 %
    private int windowMinutes;
    private BigDecimal normalRate; // 그 종목의 평소 windowMinutes분 변동 %
    private String currency;
    private Instant detectedAt;

    protected IntradayMove() {
    }

    public IntradayMove(String stockCode, BigDecimal fromPrice, BigDecimal lastPrice, BigDecimal changeRate,
                        int windowMinutes, BigDecimal normalRate, String currency, Instant detectedAt) {
        this.stockCode = stockCode;
        this.fromPrice = fromPrice;
        this.lastPrice = lastPrice;
        this.changeRate = changeRate;
        this.windowMinutes = windowMinutes;
        this.normalRate = normalRate;
        this.currency = currency;
        this.detectedAt = detectedAt;
    }

    public Long getId() { return id; }
    public String getStockCode() { return stockCode; }
    public BigDecimal getFromPrice() { return fromPrice; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public BigDecimal getChangeRate() { return changeRate; }
    public int getWindowMinutes() { return windowMinutes; }
    public BigDecimal getNormalRate() { return normalRate; }
    public String getCurrency() { return currency; }
    public Instant getDetectedAt() { return detectedAt; }
}
