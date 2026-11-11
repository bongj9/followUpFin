package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.FeedService;
import com.bongj9.briefing.application.MoveExplainer;
import com.bongj9.briefing.application.NewsTranslator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
class FeedController {

    private final FeedService feedService;
    private final NewsTranslator newsTranslator;
    private final MoveExplainer moveExplainer;

    FeedController(FeedService feedService, NewsTranslator newsTranslator, MoveExplainer moveExplainer) {
        this.feedService = feedService;
        this.newsTranslator = newsTranslator;
        this.moveExplainer = moveExplainer;
    }

    // POST /api/moves/explain {stockCode, move: 피드의 급등락 제목, at: 감지 시각} → {text: Claude가 쓴 원인 설명}
    @PostMapping("/api/moves/explain")
    Explanation explain(@RequestBody ExplainRequest req) {
        if (req.stockCode() == null || !req.stockCode().matches("\\d{6}|[A-Z][A-Z0-9.-]{0,9}")
                || req.move() == null || req.move().isBlank() || req.move().length() > 200 || req.at() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stockCode(005930·AAPL), move(200자 이하), at이 필요해요");
        }
        return new Explanation(moveExplainer.explain(req.stockCode(), req.move(), req.at()));
    }

    record ExplainRequest(String stockCode, String move, Instant at) {
    }

    record Explanation(String text) {
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
