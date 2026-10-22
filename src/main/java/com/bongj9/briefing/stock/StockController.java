package com.bongj9.briefing.stock;

import com.bongj9.briefing.news.FinnhubClient;
import com.bongj9.briefing.news.NewsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);

    private final FollowedStockRepository repository;
    private final NewsCollector newsCollector;
    private final FinnhubClient finnhub;
    private final ApplicationEventPublisher events;

    StockController(FollowedStockRepository repository, NewsCollector newsCollector, FinnhubClient finnhub,
                    ApplicationEventPublisher events) {
        this.repository = repository;
        this.newsCollector = newsCollector;
        this.finnhub = finnhub;
        this.events = events;
    }

    @GetMapping("/api/stocks")
    List<FollowedStock> list() {
        return repository.findAll();
    }

    @PostMapping("/api/stocks")
    FollowedStock follow(@RequestBody FollowRequest req) {
        String code = req.stockCode() == null ? "" : req.stockCode().strip().toUpperCase();
        if (!code.matches("\\d{6}|[A-Z]{1,5}(\\.[A-Z])?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "국내 종목코드 6자리 또는 미국 티커(AAPL)");
        }
        String name = req.name() == null ? "" : req.name().strip();
        String industry = null;
        // 미국 종목은 티커만 받으면 회사명·업종을 Finnhub에서 채운다
        if (!FollowedStock.isKoreanCode(code) && finnhub.isConfigured()) {
            FinnhubClient.Profile profile = finnhub.profile(code);
            if (profile.name() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finnhub에 없는 티커: " + code);
            }
            name = name.isEmpty() ? profile.name() : name;
            industry = profile.finnhubIndustry();
        }
        if (name.isEmpty() || name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종목명을 입력하세요");
        }
        FollowedStock stock = repository.save(new FollowedStock(code, name, industry));
        events.publishEvent(new FollowedStocksChanged());
        // 다음 수집 주기까지 기다리지 않도록 바로 한 번 수집
        try {
            newsCollector.collect(stock);
        } catch (RuntimeException e) {
            log.warn("{} 첫 뉴스 수집 실패: {}", stock.getName(), e.getMessage());
        }
        return stock;
    }

    @DeleteMapping("/api/stocks/{stockCode}")
    void unfollow(@PathVariable String stockCode) {
        repository.deleteById(stockCode);
        events.publishEvent(new FollowedStocksChanged());
    }

    record FollowRequest(String stockCode, String name) {
    }
}
