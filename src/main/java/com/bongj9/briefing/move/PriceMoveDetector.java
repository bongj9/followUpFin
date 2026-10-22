package com.bongj9.briefing.move;

import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.QuoteService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PriceMoveDetector {

    private final QuoteService quoteService;
    private final PriceMoveRepository repository;
    // 체결마다 호출되므로 오늘 이미 기록한 종목은 DB까지 가지 않고 여기서 거른다
    private final Map<String, LocalDate> recorded = new ConcurrentHashMap<>();

    public PriceMoveDetector(QuoteService quoteService, PriceMoveRepository repository) {
        this.quoteService = quoteService;
        this.repository = repository;
    }

    // 웹소켓이 끊겼거나 체결이 유실됐을 때를 대비한 주기 확인
    public List<PriceMove> detect() {
        return quoteService.quotes().stream()
                .map(this::check)
                .flatMap(Optional::stream)
                .toList();
    }

    // 큰 변동이고 오늘 아직 기록 안 했으면 저장. 같은 종목은 하루 한 번
    public synchronized Optional<PriceMove> check(Quote quote) {
        if (!quote.bigMove()) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (today.equals(recorded.get(quote.stockCode()))) {
            return Optional.empty();
        }
        recorded.put(quote.stockCode(), today);
        if (repository.existsByStockCodeAndTradeDate(quote.stockCode(), today)) {
            return Optional.empty(); // 재시작 전에 이미 기록됨
        }
        return Optional.of(repository.save(new PriceMove(quote.stockCode(), today, quote.lastPrice(),
                quote.changeRate(), quote.currency(), Instant.now())));
    }
}
