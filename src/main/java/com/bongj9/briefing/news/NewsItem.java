package com.bongj9.briefing.news;

import java.time.Instant;

// 뉴스 출처(네이버, Finnhub)가 달라도 같은 모양으로 맞춘 기사
public record NewsItem(String title, String summary, String source, String url, Instant publishedAt) {
}
