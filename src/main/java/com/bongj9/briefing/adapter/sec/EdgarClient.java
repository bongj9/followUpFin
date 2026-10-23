package com.bongj9.briefing.adapter.sec;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.DisclosureItem;
import com.bongj9.briefing.domain.FollowedStock;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 미국 공시 (SEC EDGAR). 무료·키 없음. 대신 User-Agent에 연락처를 넣어야 하고 초당 10회까지만 받는다
@Component
public class EdgarClient implements DisclosureSource {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final long MIN_INTERVAL_MS = 110; // 초당 10회 제한보다 조금 느리게

    private final RestClient restClient;
    private final boolean configured;
    private volatile Map<String, Long> ciks; // 티커 → CIK (SEC 회사 번호)
    private long lastCall;

    public EdgarClient(@Value("${sec.contact:}") String contact) {
        this.configured = !contact.isBlank();
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "followUpFin " + contact)
                .build();
    }

    @Override
    public boolean supports(String stockCode) {
        return !FollowedStock.isKoreanCode(stockCode) && configured;
    }

    @Override
    public List<DisclosureItem> fetch(LocalDate date, List<String> stockCodes) {
        return stockCodes.stream()
                .flatMap(code -> filings(code).stream())
                .filter(item -> item.rceptDt().equals(date))
                .toList();
    }

    // submissions는 최근 공시 약 1000건을 한 번에 준다. 3개월치면 충분해서 페이지 없이 잘라 쓴다
    @Override
    public Page fetchCompany(String stockCode, LocalDate from, LocalDate to, int offset, int limit) {
        List<DisclosureItem> inRange = filings(stockCode).stream()
                .filter(item -> !item.rceptDt().isBefore(from) && !item.rceptDt().isAfter(to))
                .toList();
        int start = Math.min(offset, inRange.size());
        int end = Math.min(start + limit, inRange.size());
        return new Page(inRange.subList(start, end), end < inRange.size());
    }

    // 한 회사의 최근 공시, 최신순
    private List<DisclosureItem> filings(String stockCode) {
        Long cik = ciks().get(stockCode.replace('.', '-')); // SEC는 BRK.B를 BRK-B로 쓴다
        if (cik == null) {
            return List.of();
        }
        return toItems(stockCode, cik, get(String.format("https://data.sec.gov/submissions/CIK%010d.json", cik), Submissions.class));
    }

    static List<DisclosureItem> toItems(String stockCode, long cik, Submissions s) {
        Recent r = s.filings().recent();
        List<DisclosureItem> items = new ArrayList<>();
        for (int i = 0; i < r.accessionNumber().size(); i++) {
            String accession = r.accessionNumber().get(i);
            String folder = "https://www.sec.gov/Archives/edgar/data/" + cik + "/" + accession.replace("-", "") + "/";
            String doc = r.primaryDocument().get(i);
            String title = (r.form().get(i) + " " + r.items().get(i).replace(",", " ")).strip(); // 8-K 2.02 9.01 (DisclosureGuide가 읽는 모양)
            items.add(new DisclosureItem(accession, String.valueOf(cik), s.name(), stockCode, null, title, s.name(),
                    LocalDate.parse(r.filingDate().get(i)), null,
                    doc.isEmpty() ? folder + accession + "-index.htm" : folder + doc,
                    acceptedAt(r.acceptanceDateTime().get(i))));
        }
        return items;
    }

    // submissions의 acceptanceDateTime은 뉴욕 시차를 두 번 더한 값이다 (공시 화면 Accepted 16:21:19 EDT → JSON 00:21:19Z)
    // 뉴욕 시각으로 한 번 옮긴 벽시계 값이 진짜 UTC다
    static Instant acceptedAt(String raw) {
        return LocalDateTime.ofInstant(Instant.parse(raw), NEW_YORK).toInstant(ZoneOffset.UTC);
    }

    // 전체 상장사 티커 목록(약 1만 개)을 처음 쓸 때 한 번 받는다
    // ponytail: 앱이 떠 있는 동안 새로 상장한 종목은 재시작해야 찾는다 (DartClient와 같음)
    private Map<String, Long> ciks() {
        if (ciks == null) {
            synchronized (this) {
                if (ciks == null) {
                    Map<String, Long> map = new HashMap<>();
                    get("https://www.sec.gov/files/company_tickers.json", Tickers.class)
                            .forEach((i, t) -> map.putIfAbsent(t.ticker(), t.cik()));
                    ciks = map;
                }
            }
        }
        return ciks;
    }

    // ponytail: 요청을 한 줄로 세워 간격을 둔다. 종목 수백 개면 수집이 느려지니 그때는 submissions를 캐시할 것
    private synchronized <T> T get(String url, Class<T> type) {
        long wait = lastCall + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("SEC 요청 대기 중 중단됨", e);
            }
        }
        lastCall = System.currentTimeMillis();
        return restClient.get().uri(url).retrieve().body(type);
    }

    static class Tickers extends HashMap<String, Ticker> {
    }

    record Ticker(@JsonProperty("cik_str") long cik, String ticker, String title) {
    }

    record Submissions(String name, Filings filings) {
    }

    record Filings(Recent recent) {
    }

    // 공시마다 한 칸씩, 같은 순서의 배열로 온다
    record Recent(List<String> accessionNumber, List<String> filingDate, List<String> acceptanceDateTime,
                  List<String> form, List<String> items, List<String> primaryDocument) {
    }
}
