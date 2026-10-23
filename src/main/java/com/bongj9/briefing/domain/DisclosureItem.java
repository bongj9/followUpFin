package com.bongj9.briefing.domain;

import java.time.Instant;
import java.time.LocalDate;

// 공시 출처(DART, SEC EDGAR)에서 받은 공시 한 건
// acceptedAt은 접수 시각. DART 목록엔 없어서 null, EDGAR는 초 단위로 준다
public record DisclosureItem(String rceptNo, String corpCode, String corpName, String stockCode, String corpCls,
                             String reportNm, String flrNm, LocalDate rceptDt, String rm, String url, Instant acceptedAt) {
}
