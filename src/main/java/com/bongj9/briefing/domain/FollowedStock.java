package com.bongj9.briefing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class FollowedStock {

    @Id
    private String stockCode;
    private String name; // 국내 종목은 뉴스 검색어로도 쓴다
    private String industry; // 미국 종목은 Finnhub 업종, 국내는 null
    private Instant followedAt;

    protected FollowedStock() {
    }

    public FollowedStock(String stockCode, String name, String industry) {
        this.stockCode = stockCode;
        this.name = name;
        this.industry = industry;
        this.followedAt = Instant.now();
    }

    // 국내는 숫자 6자리(005930), 미국은 티커(AAPL)
    public static boolean isKoreanCode(String stockCode) {
        return stockCode.matches("\\d{6}");
    }

    public boolean isKorean() {
        return isKoreanCode(stockCode);
    }

    public String getStockCode() { return stockCode; }
    public String getName() { return name; }
    public String getIndustry() { return industry; }
    public Instant getFollowedAt() { return followedAt; }
}
