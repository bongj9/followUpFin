package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.DisclosureItem;

import java.time.LocalDate;
import java.util.List;

// 공시 출처. 시장마다 구현체가 하나씩 있고 (국내 DART, 미국 SEC EDGAR) 종목코드로 맞는 걸 고른다
public interface DisclosureSource {

    boolean supports(String stockCode); // 설정이 없으면 false

    // 하루치 공시. DART는 시장 전체를, EDGAR는 넘겨받은 종목 것만 준다
    List<DisclosureItem> fetch(LocalDate date, List<String> stockCodes);

    // 한 종목의 기간 공시를 최신순으로 offset번째부터 최대 limit건
    Page fetchCompany(String stockCode, LocalDate from, LocalDate to, int offset, int limit);

    record Page(List<DisclosureItem> items, boolean hasMore) {
    }
}
