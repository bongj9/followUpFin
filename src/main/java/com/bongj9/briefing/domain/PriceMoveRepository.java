package com.bongj9.briefing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceMoveRepository extends JpaRepository<PriceMove, Long> {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    Optional<PriceMove> findFirstByStockCodeAndDetectedAt(String stockCode, Instant detectedAt);

    List<PriceMove> findByStockCodeInAndDetectedAtAfter(Collection<String> stockCodes, Instant since);
}
