package com.bongj9.briefing.application;

import com.bongj9.briefing.adapter.news.FinnhubClient;
import com.bongj9.briefing.adapter.news.NaverNewsClient;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsItem;
import com.bongj9.briefing.domain.NewsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"briefing.scheduling=false", "spring.datasource.url=jdbc:h2:mem:news"})
class NewsCollectorTest {

    @MockitoBean
    FinnhubClient finnhub;

    @MockitoBean
    NaverNewsClient naver;

    @Autowired
    NewsCollector collector;

    @Autowired
    NewsRepository repository;

    @Test
    void 미국_종목은_Finnhub에서_받고_같은_기사는_한_번만_저장된다() {
        FollowedStock apple = new FollowedStock("AAPL", "애플", "Technology");
        NewsItem a = item("https://example.com/1", "Apple earnings beat");
        NewsItem b = item("https://example.com/2", "iPhone sales");
        when(finnhub.supports(apple)).thenReturn(true);
        when(finnhub.fetch(apple)).thenReturn(List.of(a, b, a));

        assertThat(collector.collect(apple)).isEqualTo(2);
        assertThat(collector.collect(apple)).isZero(); // 다음 주기에 같은 기사가 와도 저장 안 함
        assertThat(repository.findAll()).extracting(News::getTitle)
                .containsExactlyInAnyOrder("Apple earnings beat", "iPhone sales");
    }

    @Test
    void 맡겠다는_출처가_없으면_건너뛴다() {
        assertThat(collector.collect(new FollowedStock("TSLA", "테슬라", null))).isZero(); // 목은 supports가 false
    }

    @Test
    void 국내_종목은_네이버로_간다() {
        FollowedStock samsung = new FollowedStock("005930", "삼성전자", null);
        when(naver.supports(samsung)).thenReturn(true);
        when(naver.fetch(samsung)).thenReturn(List.of(item("https://example.com/kr", "삼성전자 실적")));

        assertThat(collector.collect(samsung)).isEqualTo(1);
    }

    private NewsItem item(String url, String title) {
        return new NewsItem(title, "summary", "Reuters", url, Instant.parse("2026-10-21T13:00:00Z"));
    }
}
