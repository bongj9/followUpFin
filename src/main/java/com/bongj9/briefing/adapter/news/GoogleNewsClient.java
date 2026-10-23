package com.bongj9.briefing.adapter.news;

import com.bongj9.briefing.application.port.NewsSource;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.NewsItem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 국내 종목 뉴스. 구글 뉴스 RSS를 종목명으로 검색한다 (키 불필요)
// ponytail: 네이버 검색 API는 신규 신청이 막혀서 대신 씀. 피드 약관상 개인·비상업 용도만 가능
@Component
public class GoogleNewsClient implements NewsSource {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://news.google.com/rss")
            .build();

    @Override
    public boolean supports(FollowedStock stock) {
        return stock.isKorean();
    }

    @Override
    public List<NewsItem> fetch(FollowedStock stock) {
        byte[] xml = restClient.get()
                .uri(u -> u.path("/search")
                        .queryParam("q", "{q}")
                        .queryParam("hl", "ko")
                        .queryParam("gl", "KR")
                        .queryParam("ceid", "KR:ko")
                        .build("\"" + stock.getName() + "\" when:3d")) // 최근 3일, 종목명 정확히 일치
                .retrieve()
                .body(byte[].class);
        return parse(xml);
    }

    static List<NewsItem> parse(byte[] xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE 차단
            NodeList nodes = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml))
                    .getElementsByTagName("item");
            List<NewsItem> items = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String source = text(e, "source");
                String title = text(e, "title");
                // 제목 끝에 " - 매체명"이 붙어 온다
                if (source != null && title.endsWith(" - " + source)) {
                    title = title.substring(0, title.length() - source.length() - 3);
                }
                items.add(new NewsItem(title.strip(), null, source, text(e, "link"),
                        ZonedDateTime.parse(text(e, "pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()));
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("구글 뉴스 RSS를 읽지 못함: " + e.getMessage(), e);
        }
    }

    private static String text(Element e, String tag) {
        NodeList list = e.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent();
    }
}
