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

    private final FinnhubClient finnhub;
    private final NaverNewsClient naver;
    private final NewsRepository repository;

    public NewsCollector(FinnhubClient finnhub, NaverNewsClient naver, NewsRepository repository) {
        this.finnhub = finnhub;
        this.naver = naver;
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

    // 국내 종목은 종목명으로 네이버 검색, 미국 종목은 티커로 Finnhub
    private List<NewsItem> fetch(FollowedStock stock) {
        if (stock.isKorean()) {
            if (!naver.isConfigured()) {
                log.warn("secret.yml에 네이버 키가 없어 [{}] 뉴스를 건너뜀", stock.getName());
                return List.of();
            }
            return naver.search(stock.getName());
        }
        if (!finnhub.isConfigured()) {
            log.warn("secret.yml에 Finnhub 키가 없어 [{}] 뉴스를 건너뜀", stock.getStockCode());
            return List.of();
        }
        return finnhub.companyNews(stock.getStockCode());
    }
}
