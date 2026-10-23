package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.NewsItem;

import java.util.List;

// 종목명이 안 나오는 시장 전체 뉴스 (유가, 환율, 금리 등)
public interface MarketNewsSource {

    List<NewsItem> fetchMarket();
}
