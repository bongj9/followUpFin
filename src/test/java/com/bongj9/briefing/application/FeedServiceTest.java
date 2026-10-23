package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.DisclosureItem;
import com.bongj9.briefing.domain.DisclosureRepository;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.IntradayMoveRepository;
import com.bongj9.briefing.domain.NewsRepository;
import com.bongj9.briefing.domain.PriceMoveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedServiceTest {

    DisclosureSource source = mock(DisclosureSource.class);
    FeedService service = new FeedService(mock(FollowedStockRepository.class), mock(NewsRepository.class),
            mock(DisclosureRepository.class), mock(PriceMoveRepository.class), mock(IntradayMoveRepository.class), List.of(source));

    @BeforeEach
    void setUp() {
        when(source.supports("005930")).thenReturn(true);
    }

    @Test
    void 공시_탭은_사소한_공시를_건너뛰고_이어_받을_위치를_돌려준다() {
        List<DisclosureItem> all = List.of(
                item("임원ㆍ주요주주특정증권등소유상황보고서"), // 사소함
                item("주요사항보고서(자기주식취득결정)"),
                item("투자설명서(일괄신고)"), // 사소함
                item("연결재무제표기준영업(잠정)실적(공정공시)"));
        when(source.fetchCompany(eq("005930"), any(), any(), anyInt(), anyInt())).thenAnswer(call -> {
            int offset = call.getArgument(3);
            return new DisclosureSource.Page(all.subList(offset, all.size()), false);
        });

        FeedService.DisclosureList first = service.companyDisclosures("005930", 0, 1, false);
        assertThat(first.items()).extracting(FeedService.FeedItem::title).containsExactly("자사주 매입");
        assertThat(first.nextOffset()).isEqualTo(2);

        FeedService.DisclosureList second = service.companyDisclosures("005930", first.nextOffset(), 10, false);
        assertThat(second.items()).extracting(FeedService.FeedItem::title).containsExactly("잠정 실적 발표");
        assertThat(second.nextOffset()).isNull(); // 끝까지 받음

        assertThat(service.companyDisclosures("005930", 0, 10, true).items()).hasSize(4); // 사소한 공시도 보기
    }

    private static DisclosureItem item(String reportNm) {
        return new DisclosureItem("1", "00126380", "삼성전자", "005930", "Y", reportNm, "삼성전자", LocalDate.of(2026, 10, 20), "", null, null);
    }
}
