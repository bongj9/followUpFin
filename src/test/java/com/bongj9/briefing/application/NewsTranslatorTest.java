package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.Translator;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsItem;
import com.bongj9.briefing.domain.NewsRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsTranslatorTest {

    Translator translator = mock(Translator.class);
    NewsRepository repository = mock(NewsRepository.class);
    NewsTranslator service = new NewsTranslator(translator, repository);

    @Test
    void 영어_기사는_한_번만_번역해_저장하고_피드에_번역문과_원문을_준다() {
        News news = news("NVDA", "Nvidia beats estimates", "Revenue rose 60%.");
        when(repository.findById(1L)).thenReturn(Optional.of(news));
        when(translator.isConfigured()).thenReturn(true);
        when(translator.toKorean(List.of("Nvidia beats estimates", "Revenue rose 60%.")))
                .thenReturn(List.of("엔비디아, 예상치 상회", "매출이 60% 늘었다."));

        service.translate(1L);
        service.translate(1L); // 두 번째는 저장된 번역을 쓴다

        verify(translator, times(1)).toKorean(any());
        FeedService.FeedItem item = FeedService.FeedItem.news(news, "엔비디아");
        assertThat(item.title()).isEqualTo("엔비디아, 예상치 상회");
        assertThat(item.summary()).isEqualTo("매출이 60% 늘었다.");
        assertThat(item.originalTitle()).isEqualTo("Nvidia beats estimates");
    }

    @Test
    void 한국어_기사는_번역하지_않는다() {
        when(repository.findById(2L)).thenReturn(Optional.of(news("005930", "삼성전자 신고가", null)));

        service.translate(2L);

        verify(translator, never()).toKorean(any());
        assertThat(FeedService.FeedItem.news(news("005930", "삼성전자 신고가", null), "삼성전자").newsId()).isNull(); // 번역 버튼 없음
    }

    private static News news(String stockCode, String title, String summary) {
        return new News(stockCode, new NewsItem(title, summary, "Reuters", "https://example.com/" + title.hashCode(), Instant.now()), Instant.now());
    }
}
