package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.Quote;
import com.bongj9.briefing.domain.Trade;

import java.util.List;

// 실시간 체결 저장소 (TimescaleDB)
public interface TickRepository {

    void add(Quote quote);

    // 최근 체결 (오래된 순)
    List<Trade> recent(String symbol);
}
