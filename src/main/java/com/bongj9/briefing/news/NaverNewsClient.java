package com.bongj9.briefing.news;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 국내 종목 뉴스. 종목명으로 검색한다
@Component
public class NaverNewsClient {

    private final RestClient restClient;
    private final boolean configured;

    public NaverNewsClient(@Value("${naver.client-id:}") String clientId,
                           @Value("${naver.client-secret:}") String clientSecret) {
        this.configured = !clientId.isBlank() && !clientSecret.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl("https://openapi.naver.com/v1/search")
                .defaultHeader("X-Naver-Client-Id", clientId)
                .defaultHeader("X-Naver-Client-Secret", clientSecret)
                .build();
    }

    public boolean isConfigured() {
        return configured;
    }

    // 최신순 100건 (네이버 최대값)
    public List<NewsItem> search(String query) {
        SearchResponse res = restClient.get()
                .uri(u -> u.path("/news.json")
                        .queryParam("query", query)
                        .queryParam("display", 100)
                        .queryParam("sort", "date")
                        .build())
                .retrieve()
                .body(SearchResponse.class);
        return res.items().stream()
                .map(i -> new NewsItem(clean(i.title()), clean(i.description()), null, i.link(),
                        ZonedDateTime.parse(i.pubDate(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()))
                .toList();
    }

    // title, description에 <b> 태그와 HTML 엔티티가 섞여 온다
    static String clean(String html) {
        return HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", "")).strip();
    }

    record SearchResponse(List<Item> items) {
    }

    record Item(String title, String link, String description, String pubDate) {
    }
}
