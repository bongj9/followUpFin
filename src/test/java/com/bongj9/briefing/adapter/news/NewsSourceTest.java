package com.bongj9.briefing.adapter.news;

import com.bongj9.briefing.domain.FollowedStock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSourceTest {

    @Test
    void 출처는_시장으로_맡을_종목을_가른다() {
        FinnhubClient finnhubWithKey = new FinnhubClient("key");
        NaverNewsClient naverWithKey = new NaverNewsClient("id", "secret");
        FollowedStock apple = new FollowedStock("AAPL", "애플", null);
        FollowedStock samsung = new FollowedStock("005930", "삼성전자", null);

        assertThat(finnhubWithKey.supports(apple)).isTrue();
        assertThat(finnhubWithKey.supports(samsung)).isFalse();
        assertThat(naverWithKey.supports(samsung)).isTrue();
        assertThat(naverWithKey.supports(apple)).isFalse();
        assertThat(new FinnhubClient("").supports(apple)).isFalse(); // 키가 없으면 안 맡는다
    }

    @Test
    void 네이버_제목의_태그와_엔티티를_지운다() {
        assertThat(NaverNewsClient.clean("<b>삼성전자</b> &quot;실적&quot; 발표 ")).isEqualTo("삼성전자 \"실적\" 발표");
    }
}
