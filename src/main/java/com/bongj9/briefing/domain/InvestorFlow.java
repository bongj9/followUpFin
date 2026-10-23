package com.bongj9.briefing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// 하루치 투자자별 순매수 수량(주, 매도가 많으면 음수). KRX+NXT 합산
// 장중·당일 잠정치는 개인·기타법인이 null이다 (다음 영업일 새벽에 확정)
// foreignerHoldingRate: 외국인 보유율 (0.4613 = 46.13%)
public record InvestorFlow(LocalDate date, Long individual, Long foreigner, Long institution, Long otherCorporation,
                           BigDecimal foreignerHoldingRate) {
}
