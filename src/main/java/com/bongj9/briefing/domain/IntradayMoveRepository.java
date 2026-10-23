package com.bongj9.briefing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface IntradayMoveRepository extends JpaRepository<IntradayMove, Long> {

    List<IntradayMove> findByStockCodeInAndDetectedAtAfter(Collection<String> stockCodes, Instant since);
}
