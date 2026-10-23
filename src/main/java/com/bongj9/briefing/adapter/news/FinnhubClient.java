package com.bongj9.briefing.adapter.news;

import com.bongj9.briefing.application.port.CompanyProfiles;
import com.bongj9.briefing.application.port.NewsSource;
import com.bongj9.briefing.domain.CompanyProfile;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.NewsItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

// 미국 종목 뉴스. 본문은 주지 않고 제목·요약·매체·원문 링크만 준다
@Component
public class FinnhubClient implements NewsSource, CompanyProfiles {

    private static final int LOOKBACK_DAYS = 3;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://finnhub.io/api/v1")
            .build();
    private final String apiKey;

    public FinnhubClient(@Value("${finnhub.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public boolean supports(FollowedStock stock) {
        return !stock.isKorean() && isConfigured();
    }

    // 미국 종목은 티커로 찾는다
    @Override
    public List<NewsItem> fetch(FollowedStock stock) {
        return companyNews(stock.getStockCode());
    }

    public List<NewsItem> companyNews(String symbol) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        Article[] articles = restClient.get()
                .uri(u -> u.path("/company-news")
                        .queryParam("symbol", symbol)
                        .queryParam("from", to.minusDays(LOOKBACK_DAYS))
                        .queryParam("to", to)
                        .build())
                .header("X-Finnhub-Token", apiKey) // URL에 넣으면 오류 로그에 키가 찍혀서 헤더로 보낸다
                .retrieve()
                .body(Article[].class);
        return Arrays.stream(articles)
                .map(a -> new NewsItem(a.headline(), a.summary(), a.source(), a.url(), Instant.ofEpochSecond(a.datetime())))
                .toList();
    }

    // 회사명·업종. 없는 티커면 빈 객체가 와서 name이 null
    @Override
    public CompanyProfile profile(String symbol) {
        Profile p = restClient.get()
                .uri(u -> u.path("/stock/profile2").queryParam("symbol", symbol).build())
                .header("X-Finnhub-Token", apiKey)
                .retrieve()
                .body(Profile.class);
        return new CompanyProfile(p.name(), p.finnhubIndustry());
    }

    record Profile(String name, String finnhubIndustry) {
    }

    record Article(String headline, String summary, String source, String url, long datetime) {
    }
}
