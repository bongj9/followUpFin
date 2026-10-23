package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.FeedService;
import com.bongj9.briefing.application.NewsTranslator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
class FeedController {

    private final FeedService feedService;
    private final NewsTranslator newsTranslator;

    FeedController(FeedService feedService, NewsTranslator newsTranslator) {
        this.feedService = feedService;
        this.newsTranslator = newsTranslator;
    }

    // POST /api/news/123/translate → 번역한 피드 항목 (한 번 번역하면 저장해 두고 다시 부르지 않는다)
    @PostMapping("/api/news/{id}/translate")
    FeedService.FeedItem translate(@PathVariable long id) {
        try {
            return FeedService.FeedItem.news(newsTranslator.translate(id), null);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // GET /api/feed?stockCode=AAPL 또는 ?industry=Semiconductors (둘 다 없으면 전체)
    @GetMapping("/api/feed")
    List<FeedService.FeedItem> feed(@RequestParam(required = false) String stockCode,
                                    @RequestParam(required = false) String industry,
                                    @RequestParam(defaultValue = "3") int days) {
        return feedService.feed(stockCode, industry, days);
    }

    // GET /api/disclosures?stockCode=005930&offset=0&limit=2&minor=false (국내 DART·미국 SEC, 최근 3개월, 최신순. minor=true면 사소한 공시도)
    @GetMapping("/api/disclosures")
    FeedService.DisclosureList disclosures(@RequestParam String stockCode,
                                           @RequestParam(defaultValue = "0") int offset,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(defaultValue = "false") boolean minor) {
        String code = stockCode.toUpperCase();
        if (!code.matches("\\d{6}|[A-Z][A-Z0-9.-]{0,9}") || offset < 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종목코드(005930·AAPL), offset ≥ 0, limit 1~100");
        }
        return feedService.companyDisclosures(code, offset, limit, minor);
    }
}
