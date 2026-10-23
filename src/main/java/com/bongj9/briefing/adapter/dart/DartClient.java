package com.bongj9.briefing.adapter.dart;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.DisclosureItem;
import com.bongj9.briefing.domain.FollowedStock;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

@Component
public class DartClient implements DisclosureSource {

    private static final int PAGE_SIZE = 100; // DART 최대값

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr/api")
            .build();
    private final String apiKey;
    private volatile Map<String, String> corpCodes; // 종목코드 → DART 회사코드

    public DartClient(@Value("${dart.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean supports(String stockCode) {
        return FollowedStock.isKoreanCode(stockCode);
    }

    // 하루치 공시 전체를 마지막 페이지까지 받아온다 (팔로우 여부와 상관없이 시장 전체를 저장)
    @Override
    public List<DisclosureItem> fetch(LocalDate date, List<String> stockCodes) {
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        List<DisclosureItem> items = new ArrayList<>();
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
            res.list().stream().map(Item::toDisclosureItem).forEach(items::add);
            totalPage = res.totalPage();
        } while (page++ < totalPage);
        return items;
    }

    // ponytail: DART 한 페이지(100건)만 받아 잘라 쓴다. 경계를 넘는 요청은 적게 돌려주지만 hasMore가 남아 다음 요청이 이어 받는다
    @Override
    public Page fetchCompany(String stockCode, LocalDate from, LocalDate to, int offset, int limit) {
        String corpCode = corpCodes().get(stockCode);
        if (corpCode == null) {
            return new Page(List.of(), false);
        }
        int page = offset / PAGE_SIZE + 1;
        ListResponse res = fetchPage(corpCode, from.format(DateTimeFormatter.BASIC_ISO_DATE),
                to.format(DateTimeFormatter.BASIC_ISO_DATE), page);
        if ("013".equals(res.status())) {
            return new Page(List.of(), false);
        }
        if (!"000".equals(res.status())) {
            throw new IllegalStateException("DART 오류 " + res.status() + ": " + res.message());
        }
        int start = Math.min(offset % PAGE_SIZE, res.list().size());
        List<DisclosureItem> items = res.list().subList(start, Math.min(start + limit, res.list().size())).stream()
                .map(Item::toDisclosureItem)
                .toList();
        return new Page(items, offset + items.size() < res.totalCount());
    }

    private ListResponse fetchPage(String day, int page) {
        return fetchPage(null, day, day, page);
    }

    private ListResponse fetchPage(String corpCode, String from, String to, int page) {
        return restClient.get()
                .uri(u -> u.path("/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParamIfPresent("corp_code", Optional.ofNullable(corpCode))
                        .queryParam("bgn_de", from)
                        .queryParam("end_de", to)
                        .queryParam("page_no", page)
                        .queryParam("page_count", PAGE_SIZE)
                        .build())
                .retrieve()
                .body(ListResponse.class);
    }

    // DART는 회사를 자체 코드로 찾는다. 전체 회사 목록(zip, 약 3.6MB)을 처음 쓸 때 한 번 받는다
    // ponytail: 앱이 떠 있는 동안 새로 상장한 종목은 재시작해야 찾는다
    private Map<String, String> corpCodes() {
        if (corpCodes == null) {
            synchronized (this) {
                if (corpCodes == null) {
                    byte[] zip = restClient.get()
                            .uri(u -> u.path("/corpCode.xml").queryParam("crtfc_key", apiKey).build())
                            .retrieve()
                            .body(byte[].class);
                    corpCodes = parseCorpCodes(zip);
                }
            }
        }
        return corpCodes;
    }

    // 상장사(종목코드가 있는 회사)만 남긴다
    static Map<String, String> parseCorpCodes(byte[] zip) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            if (in.getNextEntry() == null) {
                throw new IllegalStateException("DART 회사코드 파일이 비어 있음 (인증키 확인)");
            }
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false); // XXE 차단
            XMLStreamReader xml = factory.createXMLStreamReader(in);
            Map<String, String> codes = new HashMap<>();
            String corpCode = null;
            while (xml.hasNext()) {
                if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (xml.getLocalName()) {
                    case "corp_code" -> corpCode = xml.getElementText().strip();
                    case "stock_code" -> {
                        String stockCode = xml.getElementText().strip();
                        if (!stockCode.isEmpty()) {
                            codes.put(stockCode, corpCode);
                        }
                    }
                    default -> { }
                }
            }
            return codes;
        } catch (IOException | XMLStreamException e) {
            throw new IllegalStateException("DART 회사코드 파일을 읽지 못함: " + e.getMessage(), e);
        }
    }

    record ListResponse(
            String status,
            String message,
            @JsonProperty("total_page") Integer totalPage, // 오류 응답엔 없다
            @JsonProperty("total_count") Integer totalCount,
            List<Item> list) {
    }

    record Item(
            @JsonProperty("rcept_no") String rceptNo,
            @JsonProperty("corp_code") String corpCode,
            @JsonProperty("corp_name") String corpName,
            @JsonProperty("stock_code") String stockCode,
            @JsonProperty("corp_cls") String corpCls,
            @JsonProperty("report_nm") String reportNm,
            @JsonProperty("flr_nm") String flrNm,
            @JsonProperty("rcept_dt") String rceptDt,
            String rm) {

        DisclosureItem toDisclosureItem() {
            return new DisclosureItem(rceptNo, corpCode, corpName, stockCode, corpCls,
                    reportNm.strip().replaceAll("\\s+", " "), // DART가 공백을 여러 칸 붙여서 보냄
                    flrNm, LocalDate.parse(rceptDt, DateTimeFormatter.BASIC_ISO_DATE), rm,
                    "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + rceptNo, null); // DART 원문 뷰어
        }
    }
}
