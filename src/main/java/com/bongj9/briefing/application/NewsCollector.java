package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketNewsSource;
import com.bongj9.briefing.application.port.NewsSource;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsItem;
import com.bongj9.briefing.domain.NewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NewsCollector {

    private static final Logger log = LoggerFactory.getLogger(NewsCollector.class);

    private final List<NewsSource> sources; // 스프링이 NewsSource 구현 빈을 전부 넣어 준다 (Finnhub, 구글 뉴스)
    private final MarketNewsSource marketSource;
    private final NewsRepository repository;

    public NewsCollector(List<NewsSource> sources, MarketNewsSource marketSource, NewsRepository repository) {
        this.sources = sources;
        this.marketSource = marketSource;
        this.repository = repository;
    }

    // 새 기사만 저장하고 저장 건수를 돌려준다
    @Transactional
    public int collect(FollowedStock stock) {
        return save(stock.getStockCode(), fetch(stock));
    }

    // 시장 뉴스는 종목 대신 News.MARKET 코드로 저장한다
    @Transactional
    public int collectMarket() {
        return save(News.MARKET, marketSource.fetchMarket());
    }

    private int save(String stockCode, List<NewsItem> items) {
        // 구글 뉴스는 같은 기사도 검색어마다 링크가 달라서 제목으로도 거른다
        Map<String, NewsItem> byLink = new LinkedHashMap<>();
        Set<String> titles = new HashSet<>();
        for (NewsItem item : items) {
            if (titles.add(item.title())) {
                byLink.putIfAbsent(item.url(), item);
            }
        }
        repository.findByStockCodeAndLinkIn(stockCode, byLink.keySet())
                .forEach(saved -> byLink.remove(saved.getLink()));
        Set<String> savedTitles = repository.findByStockCodeAndTitleIn(stockCode, titles).stream()
                .map(News::getTitle)
                .collect(Collectors.toSet());
        byLink.values().removeIf(item -> savedTitles.contains(item.title()));

        Instant now = Instant.now();
        List<News> fresh = byLink.values().stream()
                .map(item -> new News(stockCode, item, now))
                .toList();
        repository.saveAll(fresh);
        return fresh.size();
    }

    // 이 종목을 맡겠다는 출처를 고른다 (국내는 구글 뉴스, 미국은 Finnhub)
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
