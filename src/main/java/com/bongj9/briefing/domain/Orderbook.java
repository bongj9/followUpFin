package com.bongj9.briefing.domain;

import java.math.BigDecimal;
import java.util.List;

// 호가. asks는 낮은 가격순, bids는 높은 가격순
public record Orderbook(String timestamp, String currency, List<Level> asks, List<Level> bids) {

    public record Level(BigDecimal price, BigDecimal volume) {
    }
}
