package com.bongj9.briefing.adapter.sec;

import com.bongj9.briefing.domain.DisclosureGuide;
import com.bongj9.briefing.domain.DisclosureItem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EdgarClientTest {

    JsonMapper json = JsonMapper.builder().build();

    @Test
    void submissions_배열을_공시_목록으로_바꾼다() {
        String body = """
                {"cik":"320193","name":"Apple Inc.","tickers":["AAPL"],"filings":{"recent":{
                  "accessionNumber":["0000320193-26-000101","0000320193-26-000099"],
                  "filingDate":["2026-10-30","2026-10-28"],
                  "acceptanceDateTime":["2026-10-30T20:31:05.000Z","2026-10-28T22:00:00.000Z"],
                  "form":["8-K","4"],
                  "items":["2.02,9.01",""],
                  "primaryDocument":["aapl-20261030.htm",""]},"files":[]}}""";

        List<DisclosureItem> items = EdgarClient.toItems("AAPL", 320193, json.readValue(body, EdgarClient.Submissions.class));

        DisclosureItem earnings = items.getFirst();
        assertThat(earnings.reportNm()).isEqualTo("8-K 2.02 9.01");
        assertThat(earnings.rceptDt()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(earnings.acceptedAt()).isEqualTo(Instant.parse("2026-10-30T16:31:05Z")); // 원본 값에서 뉴욕 시차(4시간)를 뺀 값
        assertThat(earnings.url()).isEqualTo("https://www.sec.gov/Archives/edgar/data/320193/000032019326000101/aapl-20261030.htm");
        assertThat(items.get(1).reportNm()).isEqualTo("4");
        assertThat(items.get(1).url()).endsWith("/000032019326000099/0000320193-26-000099-index.htm");
    }

    @Test
    void 접수_시각의_뉴욕_시차를_바로잡는다() {
        // SEC 공시 화면의 Accepted 시각(미국 동부)과 비교한 실제 값
        assertThat(EdgarClient.acceptedAt("2026-08-27T00:21:19.000Z")).isEqualTo(Instant.parse("2026-08-26T20:21:19Z")); // 16:21:19 EDT
        assertThat(EdgarClient.acceptedAt("2026-02-27T04:34:19.000Z")).isEqualTo(Instant.parse("2026-02-26T23:34:19Z")); // 18:34:19 EST
    }

    @Test
    void 티커_목록을_읽는다() {
        EdgarClient.Tickers tickers = json.readValue("""
                {"0":{"cik_str":320193,"ticker":"AAPL","title":"Apple Inc."}}""", EdgarClient.Tickers.class);
        assertThat(tickers.get("0").cik()).isEqualTo(320193L);
    }

    @Test
    void SEC_양식과_8K_항목을_쉬운_이름으로_바꾼다() {
        assertThat(DisclosureGuide.of("8-K 2.02 9.01").name()).isEqualTo("실적 발표");
        assertThat(DisclosureGuide.of("8-K 5.02 9.01").importance()).isEqualTo(DisclosureGuide.Importance.MID);
        assertThat(DisclosureGuide.of("8-K 9.01").name()).isEqualTo("수시 공시 (8-K)");
        assertThat(DisclosureGuide.of("10-K/A").name()).isEqualTo("연간 보고서 (10-K)");
        assertThat(DisclosureGuide.of("4").importance()).isEqualTo(DisclosureGuide.Importance.LOW); // 기본으로 숨김
        assertThat(DisclosureGuide.of("424B4").importance()).isEqualTo(DisclosureGuide.Importance.MID); // 규칙에 없으면 원래 이름
        assertThat(DisclosureGuide.of("SCHEDULE 13G").importance()).isEqualTo(DisclosureGuide.Importance.LOW);
    }
}
