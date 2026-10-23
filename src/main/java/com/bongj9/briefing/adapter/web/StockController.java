package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.StockService;
import com.bongj9.briefing.domain.FollowedStock;
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

    private final StockService stocks;

    StockController(StockService stocks) {
        this.stocks = stocks;
    }

    @GetMapping("/api/stocks")
    List<FollowedStock> list() {
        return stocks.list();
    }

    @PostMapping("/api/stocks")
    FollowedStock follow(@RequestBody FollowRequest req) {
        try {
            return stocks.follow(req.stockCode(), req.name());
        } catch (StockService.InvalidStockException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // 토스 계좌의 보유 종목을 관심종목으로 가져온다 (이미 있는 건 건너뜀)
    @PostMapping("/api/stocks/import-holdings")
    StockService.ImportResult importHoldings() {
        return stocks.importHoldings();
    }

    @DeleteMapping("/api/stocks/{stockCode}")
    void unfollow(@PathVariable String stockCode) {
        stocks.unfollow(stockCode);
    }

    record FollowRequest(String stockCode, String name) {
    }
}
