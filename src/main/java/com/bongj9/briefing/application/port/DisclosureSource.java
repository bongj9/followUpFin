package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.DisclosureItem;

import java.time.LocalDate;
import java.util.List;

// 공시 출처 (DART)
public interface DisclosureSource {

    // 하루치 공시 전체
    List<DisclosureItem> fetch(LocalDate date);
}
