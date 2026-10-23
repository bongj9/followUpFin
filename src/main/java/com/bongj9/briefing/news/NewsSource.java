package com.bongj9.briefing.news;

import com.bongj9.briefing.stock.FollowedStock;

import java.util.List;

// 뉴스 출처. 시장마다 구현체가 하나씩 있고, NewsCollector가 종목에 맞는 걸 고른다
public interface NewsSource {

    boolean supports(FollowedStock stock); // 키가 없으면 false

    List<NewsItem> fetch(FollowedStock stock);
}
