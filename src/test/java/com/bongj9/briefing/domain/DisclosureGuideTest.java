package com.bongj9.briefing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureGuideTest {

    @Test
    void 어려운_공시_제목을_쉬운_이름과_중요도로_바꾼다() {
        DisclosureGuide earnings = DisclosureGuide.of("연결재무제표기준영업(잠정)실적(공정공시)");
        assertThat(earnings.name()).isEqualTo("잠정 실적 발표");
        assertThat(earnings.importance()).isEqualTo(DisclosureGuide.Importance.HIGH);
        assertThat(earnings.tip()).contains("영업이익");

        assertThat(DisclosureGuide.of("임원ㆍ주요주주특정증권등소유상황보고서").importance()).isEqualTo(DisclosureGuide.Importance.LOW);
        assertThat(DisclosureGuide.of("주요사항보고서(유상증자결정)").name()).isEqualTo("유상증자");
        assertThat(DisclosureGuide.of("주요사항보고서(자기주식취득결정)").name()).isEqualTo("자사주 매입");
        assertThat(DisclosureGuide.of("자기주식취득결과보고서").name()).isEqualTo("자사주 매입 완료");
    }

    @Test
    void 정정_표시는_무시하고_모르는_공시는_원래_제목에_중간_중요도() {
        assertThat(DisclosureGuide.of("[기재정정]단일판매ㆍ공급계약체결").name()).isEqualTo("대형 공급계약");
        DisclosureGuide unknown = DisclosureGuide.of("[기재정정]처음보는공시");
        assertThat(unknown.name()).isEqualTo("처음보는공시");
        assertThat(unknown.importance()).isEqualTo(DisclosureGuide.Importance.MID);
        assertThat(unknown.tip()).isNull();
    }
}
