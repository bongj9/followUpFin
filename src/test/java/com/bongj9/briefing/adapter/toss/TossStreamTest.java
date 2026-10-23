package com.bongj9.briefing.adapter.toss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TossStreamTest {

    @Test
    void 종목마다_체결과_호가를_구독한다() {
        assertThat(TossStream.declaration(Map.of(false, List.of("AAPL"), true, List.of("005930")))).containsExactly(
                Map.of("type", "trade:us", "codes", List.of("AAPL")),
                Map.of("type", "trade:kr", "codes", List.of("005930")),
                Map.of("type", "orderbook:us", "codes", List.of("AAPL")),
                Map.of("type", "orderbook:kr", "codes", List.of("005930")));
    }

    @Test
    void 구독_100건을_넘으면_체결을_먼저_채우고_남는_자리만_호가에_쓴다() {
        List<String> us = IntStream.range(0, 60).mapToObj(i -> "US" + i).toList();
        List<Map<String, Object>> declaration = TossStream.declaration(Map.of(false, us, true, List.of()));

        assertThat(declaration).extracting(d -> d.get("type")).containsExactly("trade:us", "orderbook:us");
        assertThat((List<?>) declaration.get(0).get("codes")).hasSize(60);
        assertThat((List<?>) declaration.get(1).get("codes")).hasSize(40);
    }
}
