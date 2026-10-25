package com.bongj9.briefing.domain;

import java.time.LocalDate;

// 하루치 시장(코스피·코스닥) 전체 투자자별 순매수 금액(원, 매도가 많으면 음수). 당일은 장 마감 전까지 잠정치
public record MarketFlow(LocalDate date, long individual, long foreigner, long institution) {
}
