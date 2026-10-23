package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.DisclosureItem;

import java.time.LocalDate;
import java.util.List;

// 공시 출처 (DART)
public interface DisclosureSource {

    // 하루치 공시 전체
    List<DisclosureItem> fetch(LocalDate date);

    // 한 종목의 기간 공시를 최신순으로 offset번째부터 최대 limit건
    Page fetchCompany(String stockCode, LocalDate from, LocalDate to, int offset, int limit);

    record Page(List<DisclosureItem> items, boolean hasMore) {
    }
}
