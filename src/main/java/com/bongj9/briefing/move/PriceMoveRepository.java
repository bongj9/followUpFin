package com.bongj9.briefing.move;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PriceMoveRepository extends JpaRepository<PriceMove, Long> {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    List<PriceMove> findByStockCodeInAndDetectedAtAfter(Collection<String> stockCodes, Instant since);
}
