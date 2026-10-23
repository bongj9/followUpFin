package com.bongj9.briefing.application;

import com.bongj9.briefing.domain.PriceMove;
import com.bongj9.briefing.domain.PriceMoveRepository;
import com.bongj9.briefing.domain.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"briefing.scheduling=false", "spring.datasource.url=jdbc:h2:mem:move"})
class PriceMoveDetectorTest {

    @MockitoBean
    QuoteService quoteService;

    @Autowired
    PriceMoveDetector detector;

    @Autowired
    PriceMoveRepository repository;

    @Test
    void 큰_변동만_하루_한_번_기록한다() {
        when(quoteService.quotes()).thenReturn(List.of(
                quote("AMD", "-6.20", true),
                quote("AAPL", "-0.26", false)));

        assertThat(detector.detect()).extracting(PriceMove::getStockCode).containsExactly("AMD");
        assertThat(detector.detect()).isEmpty(); // 10분 뒤 다시 확인해도 오늘은 이미 기록됨
        assertThat(repository.findAll()).filteredOn(m -> m.getStockCode().equals("AMD")).hasSize(1);
    }

    @Test
    void 체결마다_확인해도_하루_한_번만_기록한다() {
        assertThat(detector.check(quote("NVDA", "5.10", true))).isPresent();
        assertThat(detector.check(quote("NVDA", "5.30", true))).isEmpty();
        assertThat(detector.check(quote("TSLA", "1.00", false))).isEmpty();
    }

    private Quote quote(String code, String rate, boolean bigMove) {
        return new Quote(code, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal(rate), "USD", null, bigMove, null);
    }
}
