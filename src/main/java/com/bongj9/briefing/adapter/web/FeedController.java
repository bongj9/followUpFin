package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.FeedService;
import com.bongj9.briefing.domain.FollowedStock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    // GET /api/disclosures?stockCode=005930&offset=0&limit=2&minor=false (국내 종목, 최근 3개월, 최신순. minor=true면 사소한 공시도)
    @GetMapping("/api/disclosures")
    FeedService.DisclosureList disclosures(@RequestParam String stockCode,
                                           @RequestParam(defaultValue = "0") int offset,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(defaultValue = "false") boolean minor) {
        if (!FollowedStock.isKoreanCode(stockCode) || offset < 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "국내 종목코드, offset ≥ 0, limit 1~100");
        }
        return feedService.companyDisclosures(stockCode, offset, limit, minor);
    }
}
