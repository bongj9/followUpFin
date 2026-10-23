package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.QuoteService;
import com.bongj9.briefing.domain.Quote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class QuoteController {

    private final QuoteService quoteService;

    QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/api/quotes")
    List<Quote> quotes() {
        return quoteService.quotes();
    }
}
