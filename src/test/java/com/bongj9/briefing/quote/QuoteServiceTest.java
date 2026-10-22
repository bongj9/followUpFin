package com.bongj9.briefing.quote;

import com.bongj9.briefing.stock.FollowedStockRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteServiceTest {

    @Test
    void 체결마다_일봉을_다시_부르지_않는다() {
        TossClient toss = mock(TossClient.class);
        when(toss.dailyCandles("AMD", 2)).thenReturn(List.of(
                new TossClient.Candle("2026-10-22T09:00:00+09:00", "1", "1", "1", "105", "1"),
                new TossClient.Candle("2026-10-21T09:00:00+09:00", "1", "1", "1", "100", "1")));
        QuoteService service = new QuoteService(toss, mock(FollowedStockRepository.class), new BigDecimal("5"));

        Quote first = service.toQuote("AMD", new BigDecimal("105"), "USD", null, null);
        Quote second = service.toQuote("AMD", new BigDecimal("94"), "USD", null, null);

        assertThat(first.changeRate()).isEqualByComparingTo("5.00");
        assertThat(first.bigMove()).isTrue();
        assertThat(second.changeRate()).isEqualByComparingTo("-6.00");
        verify(toss, times(1)).dailyCandles("AMD", 2);
    }
}
