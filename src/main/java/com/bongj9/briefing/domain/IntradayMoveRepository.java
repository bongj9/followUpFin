package com.bongj9.briefing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntradayMoveRepository extends JpaRepository<IntradayMove, Long> {

    Optional<IntradayMove> findFirstByStockCodeAndDetectedAt(String stockCode, Instant detectedAt);

    List<IntradayMove> findByStockCodeInAndDetectedAtAfter(Collection<String> stockCodes, Instant since);
}
