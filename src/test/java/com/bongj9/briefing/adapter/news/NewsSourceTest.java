package com.bongj9.briefing.adapter.news;

import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.NewsItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSourceTest {

    @Test
    void 출처는_시장으로_맡을_종목을_가른다() {
        FinnhubClient finnhubWithKey = new FinnhubClient("key");
        GoogleNewsClient google = new GoogleNewsClient();
        FollowedStock apple = new FollowedStock("AAPL", "애플", null);
        FollowedStock samsung = new FollowedStock("005930", "삼성전자", null);

        assertThat(finnhubWithKey.supports(apple)).isTrue();
        assertThat(finnhubWithKey.supports(samsung)).isFalse();
        assertThat(google.supports(samsung)).isTrue();
        assertThat(google.supports(apple)).isFalse();
        assertThat(new FinnhubClient("").supports(apple)).isFalse(); // 키가 없으면 안 맡는다
    }

    @Test
    void 구글_뉴스_RSS에서_제목의_매체명을_떼고_매체를_따로_담는다() {
        String xml = """
                <rss><channel><item>
                <title>삼성전자, 2나노 모뎀 - 디일렉 - 디일렉</title>
                <link>https://news.google.com/rss/articles/abc</link>
                <pubDate>Thu, 22 Oct 2026 11:49:00 GMT</pubDate>
                <source url="https://www.thelec.kr">디일렉</source>
                </item></channel></rss>""";

        NewsItem item = GoogleNewsClient.parse(xml.getBytes(StandardCharsets.UTF_8)).getFirst();

        assertThat(item.title()).isEqualTo("삼성전자, 2나노 모뎀");
        assertThat(item.source()).isEqualTo("디일렉");
        assertThat(item.url()).isEqualTo("https://news.google.com/rss/articles/abc");
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-10-22T11:49:00Z"));
    }
}
