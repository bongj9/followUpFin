package com.bongj9.briefing.news;

import com.bongj9.briefing.stock.FollowedStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NewsCollector {

    private static final Logger log = LoggerFactory.getLogger(NewsCollector.class);

    private final List<NewsSource> sources; // 스프링이 NewsSource 구현 빈을 전부 넣어 준다 (Finnhub, 네이버)
    private final NewsRepository repository;

    public NewsCollector(List<NewsSource> sources, NewsRepository repository) {
        this.sources = sources;
        this.repository = repository;
    }

    // 새 기사만 저장하고 저장 건수를 돌려준다
    @Transactional
    public int collect(FollowedStock stock) {
        List<NewsItem> items = fetch(stock);
        Map<String, NewsItem> byLink = new LinkedHashMap<>();
        for (NewsItem item : items) {
            byLink.putIfAbsent(item.url(), item);
        }
        repository.findByStockCodeAndLinkIn(stock.getStockCode(), byLink.keySet())
                .forEach(saved -> byLink.remove(saved.getLink()));

        Instant now = Instant.now();
        List<News> fresh = byLink.values().stream()
                .map(item -> new News(stock.getStockCode(), item, now))
                .toList();
        repository.saveAll(fresh);
        return fresh.size();
    }

    // 이 종목을 맡겠다는 출처를 고른다 (국내는 네이버, 미국은 Finnhub)
    private List<NewsItem> fetch(FollowedStock stock) {
        return sources.stream()
                .filter(source -> source.supports(stock))
                .findFirst()
                .map(source -> source.fetch(stock))
                .orElseGet(() -> {
                    log.warn("[{}] 뉴스 출처가 없어 건너뜀 (secret.yml 키 확인)", stock.getName());
                    return List.of();
                });
    }
}
