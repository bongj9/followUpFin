package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.Llm;
import com.bongj9.briefing.domain.DisclosureRepository;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsItem;
import com.bongj9.briefing.domain.NewsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveExplainerTest {

    static final Instant AT = Instant.parse("2026-11-09T02:00:00Z"); // 한국 시간 11:00

    Llm llm = mock(Llm.class);
    NewsRepository newsRepository = mock(NewsRepository.class);
    MoveExplainer explainer = new MoveExplainer(llm, newsRepository, mock(DisclosureRepository.class));

    @Test
    void 감지_뒤_1시간이_지난_기사는_빼고_최신순으로_자른다() {
        News late = news("감지 2시간 뒤", AT.plus(Duration.ofHours(2)));
        News justAfter = news("감지 30분 뒤", AT.plus(Duration.ofMinutes(30)));
        News before = news("전날 밤", AT.minus(Duration.ofHours(12)));

        assertThat(MoveExplainer.around(List.of(before, late, justAfter), AT, 30)).containsExactly(justAfter, before);
        assertThat(MoveExplainer.around(List.of(before, justAfter), AT, 1)).containsExactly(justAfter);
    }

    @Test
    void 질문에_급등락과_번호_붙인_제목이_들어가고_없는_목록은_없음으로_적는다() {
        String prompt = MoveExplainer.prompt("삼성전자 전일 대비 +7.30% 급등", AT,
                List.of(news("삼성전자, HBM 공급 계약", AT.minus(Duration.ofHours(1)))), List.of(), List.of());

        assertThat(prompt).contains("삼성전자 전일 대비 +7.30% 급등", "2026-11-09T11:00", "[1] 2026-11-09T10:00 삼성전자, HBM 공급 계약");
        assertThat(prompt.split("\\(없음\\)", -1)).hasSize(3); // 공시·시장 뉴스 두 곳
    }

    @Test
    void 같은_급등락은_한_번만_묻는다() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.ask(anyString(), anyString())).thenReturn("HBM 계약 소식에 올랐어요 [1]");

        explainer.explain("005930", "급등", AT);
        assertThat(explainer.explain("005930", "급등", AT)).isEqualTo("HBM 계약 소식에 올랐어요 [1]");
        verify(llm, times(1)).ask(anyString(), anyString());
    }

    @Test
    void 키가_없으면_이유를_알려준다() {
        assertThatThrownBy(() -> explainer.explain("005930", "급등", AT)).hasMessageContaining("anthropic.api-key");
    }

    static News news(String title, Instant publishedAt) {
        return new News("005930", new NewsItem(title, null, null, "https://example.com/" + title.hashCode(), publishedAt), publishedAt);
    }
}
