package com.bongj9.briefing.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findByStockCodeAndLinkIn(String stockCode, Collection<String> links);

    List<News> findByStockCodeInAndPublishedAtAfter(Collection<String> stockCodes, Instant since);
}
