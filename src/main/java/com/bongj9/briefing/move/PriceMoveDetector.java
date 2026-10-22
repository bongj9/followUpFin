package com.bongj9.briefing.move;

import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.QuoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class PriceMoveDetector {

    private final QuoteService quoteService;
    private final PriceMoveRepository repository;

    public PriceMoveDetector(QuoteService quoteService, PriceMoveRepository repository) {
        this.quoteService = quoteService;
        this.repository = repository;
    }

    // 큰 변동 종목 중 오늘 아직 기록 안 한 것만 저장하고 새로 감지한 목록을 돌려준다
    @Transactional
    public List<PriceMove> detect() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Instant now = Instant.now();
        List<PriceMove> fresh = quoteService.quotes().stream()
                .filter(Quote::bigMove)
                .filter(q -> !repository.existsByStockCodeAndTradeDate(q.stockCode(), today))
                .map(q -> new PriceMove(q.stockCode(), today, q.lastPrice(), q.changeRate(), q.currency(), now))
                .toList();
        return repository.saveAll(fresh);
    }
}
