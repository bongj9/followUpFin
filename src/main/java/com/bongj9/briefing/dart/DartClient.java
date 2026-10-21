package com.bongj9.briefing.dart;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DartClient {

    private static final int PAGE_SIZE = 100; // DART 최대값

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr/api")
            .build();
    private final String apiKey;

    public DartClient(@Value("${dart.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    // 하루치 공시 전체를 마지막 페이지까지 받아온다
    public List<Item> fetchList(LocalDate date) {
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Item> items = new ArrayList<>();
        int page = 1;
        int totalPage;
        do {
            ListResponse res = fetchPage(day, page);
            if ("013".equals(res.status())) {
                return items; // 조회된 데이터 없음
            }
            if (!"000".equals(res.status())) {
                throw new IllegalStateException("DART 오류 " + res.status() + ": " + res.message());
            }
            items.addAll(res.list());
            totalPage = res.totalPage();
        } while (page++ < totalPage);
        return items;
    }

    private ListResponse fetchPage(String day, int page) {
        return restClient.get()
                .uri(u -> u.path("/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("bgn_de", day)
                        .queryParam("end_de", day)
                        .queryParam("page_no", page)
                        .queryParam("page_count", PAGE_SIZE)
                        .build())
                .retrieve()
                .body(ListResponse.class);
    }

    record ListResponse(
            String status,
            String message,
            @JsonProperty("total_page") int totalPage,
            List<Item> list) {
    }

    public record Item(
            @JsonProperty("rcept_no") String rceptNo,
            @JsonProperty("corp_code") String corpCode,
            @JsonProperty("corp_name") String corpName,
            @JsonProperty("stock_code") String stockCode,
            @JsonProperty("corp_cls") String corpCls,
            @JsonProperty("report_nm") String reportNm,
            @JsonProperty("flr_nm") String flrNm,
            @JsonProperty("rcept_dt") String rceptDt,
            String rm) {
    }
}
