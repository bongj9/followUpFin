package com.bongj9.briefing.adapter.toss;

import com.bongj9.briefing.adapter.toss.TossClient.InvestorTradingResponse;
import com.bongj9.briefing.adapter.toss.TossClient.TossCandle;
import com.bongj9.briefing.domain.InvestorFlow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TossClientTest {

    @Test
    void 문자열_봉을_숫자로_바꾸고_비어_있는_봉은_버린다() {
        var candle = TossCandle.toCandleOrNull(
                new TossCandle("2026-10-22T09:00:00+09:00", "1", "3", "0.5", "2.25", "100"));
        assertThat(candle.close()).isEqualByComparingTo("2.25");
        assertThat(candle.time()).hasToString("2026-10-22T00:00:00Z");

        assertThat(TossCandle.toCandleOrNull(null)).isNull();
        assertThat(TossCandle.toCandleOrNull(
                new TossCandle("2026-10-23T09:00:00+09:00", "1", "1", "1", "1", null))).isNull();
    }

    @Test
    void 투자자별_순매수를_읽고_당일_잠정치의_빈_칸은_null로_둔다() {
        // 2026-10-23 장중 삼성전자 실제 응답 모양 (개인·기타법인은 아직 집계 전)
        String json = """
                {"result": {"nextUntil": "2026-10-21", "records": [
                  {"date": "2026-10-23", "individual": null,
                   "foreigner": {"buyVolume": "2340485", "sellVolume": "2732074", "netBuyVolume": "-391589"},
                   "institution": {"buyVolume": "1231000", "sellVolume": "1149000", "netBuyVolume": "82000", "breakdown": null},
                   "otherCorporation": null,
                   "foreignerHolding": {"holdingQuantity": "2696659350", "limitQuantity": "5846278608", "holdingRate": "0.4613"},
                   "cfd": null},
                  {"date": "2026-10-22",
                   "individual": {"netBuyVolume": "2165872"}, "foreigner": {"netBuyVolume": "-1041108"},
                   "institution": {"netBuyVolume": "-1180703"}, "otherCorporation": {"netBuyVolume": "42683"},
                   "foreignerHolding": null}
                ]}}""";
        List<InvestorFlow> flows = JsonMapper.builder().build().readValue(json, InvestorTradingResponse.class)
                .result().records().stream().map(TossClient.TossInvestorRecord::toFlow).toList();

        assertThat(flows.get(0)).isEqualTo(new InvestorFlow(LocalDate.of(2026, 10, 23), null, -391589L, 82000L, null,
                new java.math.BigDecimal("0.4613")));
        assertThat(flows.get(1).individual()).isEqualTo(2165872L);
        assertThat(flows.get(1).foreignerHoldingRate()).isNull();
    }
}
