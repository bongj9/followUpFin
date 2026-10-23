package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.FeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class FeedController {

    private final FeedService feedService;

    FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    // GET /api/feed?stockCode=AAPL 또는 ?industry=Semiconductors (둘 다 없으면 전체)
    @GetMapping("/api/feed")
    List<FeedService.FeedItem> feed(@RequestParam(required = false) String stockCode,
                                    @RequestParam(required = false) String industry,
                                    @RequestParam(defaultValue = "3") int days) {
        return feedService.feed(stockCode, industry, days);
    }
}
