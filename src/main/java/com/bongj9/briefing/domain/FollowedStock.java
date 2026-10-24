package com.bongj9.briefing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

    // 레버리지 ETF는 자체 뉴스가 거의 없어 기초 종목·시장 뉴스를 대신 받는다 (Finnhub은 티커, 주요 매체는 이름으로 찾는다)
    // ponytail: 계좌에 있는 것만 적었다. 다른 ETF는 여기에 한 줄 추가
    private static final Map<String, FollowedStock> ETF_UNDERLYING = Map.of(
            "MUU", new FollowedStock("MU", "Micron", null),
            "AMDL", new FollowedStock("AMD", "AMD", null),
            "SNXX", new FollowedStock("SNDK", "Sandisk", null),
            "SOXL", new FollowedStock("SOXX", "semiconductor", null),
            "KORU", new FollowedStock("EWY", "Korean stocks", null));

    public static Optional<FollowedStock> etfUnderlying(String stockCode) {
        return Optional.ofNullable(ETF_UNDERLYING.get(stockCode));
    }

    // 뉴스를 찾을 대상: 레버리지 ETF는 기초 종목, 나머지는 자기 자신
    public FollowedStock newsTarget() {
        return ETF_UNDERLYING.getOrDefault(stockCode, this);
    }

    public String getStockCode() { return stockCode; }
    public String getName() { return name; }
    public String getIndustry() { return industry; }
    public Instant getFollowedAt() { return followedAt; }
}
