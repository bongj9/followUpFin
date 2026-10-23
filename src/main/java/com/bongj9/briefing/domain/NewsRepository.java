package com.bongj9.briefing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findByStockCodeAndLinkIn(String stockCode, Collection<String> links);

    List<News> findByStockCodeAndTitleIn(String stockCode, Collection<String> titles);

    List<News> findByStockCodeInAndPublishedAtAfter(Collection<String> stockCodes, Instant since);
}
