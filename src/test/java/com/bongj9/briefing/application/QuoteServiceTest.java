package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteServiceTest {

    @Test
    void 체결마다_일봉을_다시_부르지_않는다() {
        MarketData market = mock(MarketData.class);
        when(market.dailyCandles("AMD", 2)).thenReturn(List.of(
                candle("2026-10-21T00:00:00Z", "100"),
                candle("2026-10-22T00:00:00Z", "105"))); // 순서가 뒤바뀌어 와도 최신 봉을 오늘로 본다
        QuoteService service = new QuoteService(market, mock(FollowedStockRepository.class), new BigDecimal("5"));

        Quote first = service.toQuote("AMD", new BigDecimal("105"), "USD", null, null);
        Quote second = service.toQuote("AMD", new BigDecimal("94"), "USD", null, null);

        assertThat(first.changeRate()).isEqualByComparingTo("5.00");
        assertThat(first.bigMove()).isTrue();
        assertThat(second.changeRate()).isEqualByComparingTo("-6.00");
        verify(market, times(1)).dailyCandles("AMD", 2);
    }

    private static Candle candle(String time, String close) {
        BigDecimal one = BigDecimal.ONE;
        return new Candle(Instant.parse(time), one, one, one, new BigDecimal(close), one);
    }
}
