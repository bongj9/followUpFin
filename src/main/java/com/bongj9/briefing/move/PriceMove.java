package com.bongj9.briefing.move;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// 전일 대비 큰 변동 감지 기록. 같은 종목은 하루 한 번만 (DB 유니크 제약으로도 막는다)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"stockCode", "tradeDate"}))
public class PriceMove {

    @Id
    @GeneratedValue
    private Long id;
    private String stockCode;
    private LocalDate tradeDate; // 감지한 날 (한국 시간)
    private BigDecimal lastPrice;
    private BigDecimal changeRate;
    private String currency;
    private Instant detectedAt;

    protected PriceMove() {
    }

    public PriceMove(String stockCode, LocalDate tradeDate, BigDecimal lastPrice, BigDecimal changeRate,
                     String currency, Instant detectedAt) {
        this.stockCode = stockCode;
        this.tradeDate = tradeDate;
        this.lastPrice = lastPrice;
        this.changeRate = changeRate;
        this.currency = currency;
        this.detectedAt = detectedAt;
    }

    public Long getId() { return id; }
    public String getStockCode() { return stockCode; }
    public LocalDate getTradeDate() { return tradeDate; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public BigDecimal getChangeRate() { return changeRate; }
    public String getCurrency() { return currency; }
    public Instant getDetectedAt() { return detectedAt; }
}
