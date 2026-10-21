package com.bongj9.briefing.news;

import com.bongj9.briefing.stock.FollowedStock;
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
        when(finnhub.isConfigured()).thenReturn(true);
        when(finnhub.companyNews("AAPL")).thenReturn(List.of(a, b, a));

        assertThat(collector.collect(apple)).isEqualTo(2);
        assertThat(collector.collect(apple)).isZero(); // 다음 주기에 같은 기사가 와도 저장 안 함
        assertThat(repository.findAll()).extracting(News::getTitle)
                .containsExactlyInAnyOrder("Apple earnings beat", "iPhone sales");
    }

    @Test
    void 키가_없으면_건너뛴다() {
        when(finnhub.isConfigured()).thenReturn(false);
        assertThat(collector.collect(new FollowedStock("TSLA", "테슬라", null))).isZero();
    }

    @Test
    void 네이버_제목의_태그와_엔티티를_지운다() {
        assertThat(NaverNewsClient.clean("<b>삼성전자</b> &quot;실적&quot; 발표 ")).isEqualTo("삼성전자 \"실적\" 발표");
    }

    private NewsItem item(String url, String title) {
        return new NewsItem(title, "summary", "Reuters", url, Instant.parse("2026-10-21T13:00:00Z"));
    }
}
