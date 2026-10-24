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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class NewsCollector {

    private static final Logger log = LoggerFactory.getLogger(NewsCollector.class);

    // 스프링이 구현 빈을 전부 넣어 준다. 종목: Finnhub·구글 뉴스·주요 매체, 시장: 구글 뉴스(국내)·주요 매체(미국)
    private final List<NewsSource> sources;
    private final List<MarketNewsSource> marketSources;
    private final NewsRepository repository;

    public NewsCollector(List<NewsSource> sources, List<MarketNewsSource> marketSources, NewsRepository repository) {
        this.sources = sources;
        this.marketSources = marketSources;
        this.repository = repository;
    }

    // 새 기사만 저장하고 저장 건수를 돌려준다
    @Transactional
    public int collect(FollowedStock stock) {
        return save(stock.getStockCode(), fetch(stock.newsTarget()));
    }

    // 시장 뉴스는 종목 대신 News.MARKET 코드로 저장한다
    @Transactional
    public int collectMarket() {
        List<NewsItem> items = new ArrayList<>();
        for (MarketNewsSource source : marketSources) {
            items.addAll(fetchSafely(source.getClass().getSimpleName(), "시장", source::fetchMarket));
        }
        return save(News.MARKET, items);
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

    // 이 종목을 맡겠다는 출처에서 모두 받는다 (국내는 구글 뉴스, 미국은 Finnhub + 주요 매체)
    private List<NewsItem> fetch(FollowedStock stock) {
        List<NewsSource> supporting = sources.stream().filter(source -> source.supports(stock)).toList();
        if (supporting.isEmpty()) {
            log.warn("[{}] 뉴스 출처가 없어 건너뜀 (secret.yml 키 확인)", stock.getName());
        }
        List<NewsItem> items = new ArrayList<>();
        for (NewsSource source : supporting) {
            items.addAll(fetchSafely(source.getClass().getSimpleName(), stock.getName(), () -> source.fetch(stock)));
        }
        return items;
    }

    // 출처 하나가 실패해도 나머지 기사는 저장한다
    private List<NewsItem> fetchSafely(String source, String target, Supplier<List<NewsItem>> fetch) {
        try {
            return fetch.get();
        } catch (RuntimeException e) {
            log.error("뉴스 수집 실패 [{} / {}]: {}", source, target, e.getMessage());
            return List.of();
        }
    }
}
