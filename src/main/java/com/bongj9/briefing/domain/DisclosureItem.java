package com.bongj9.briefing.domain;

import java.time.LocalDate;

// 공시 출처(DART)에서 받은 공시 한 건
public record DisclosureItem(String rceptNo, String corpCode, String corpName, String stockCode, String corpCls,
                             String reportNm, String flrNm, LocalDate rceptDt, String rm) {
}
