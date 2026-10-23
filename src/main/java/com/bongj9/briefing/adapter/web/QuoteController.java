package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.IndicatorService;
import com.bongj9.briefing.application.QuoteService;
import com.bongj9.briefing.domain.Quote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class QuoteController {

    private final QuoteService quoteService;
    private final IndicatorService indicatorService;

    QuoteController(QuoteService quoteService, IndicatorService indicatorService) {
        this.quoteService = quoteService;
        this.indicatorService = indicatorService;
    }

    @GetMapping("/api/quotes")
    List<Quote> quotes() {
        return quoteService.quotes();
    }

    // 상단 지표 띠 (코스피, 코스닥, 미국 지수 ETF, 원/달러)
    @GetMapping("/api/indicators")
    List<IndicatorService.Indicator> indicators() {
        return indicatorService.indicators();
    }
}
