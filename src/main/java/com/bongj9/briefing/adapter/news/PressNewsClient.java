package com.bongj9.briefing.adapter.news;

import com.bongj9.briefing.application.port.MarketNewsSource;
import com.bongj9.briefing.application.port.NewsSource;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.NewsItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 미국 주요 매체(로이터·블룸버그·CNBC) 기사. 공식 API는 기업용이고 공개 RSS도 없어서 구글 뉴스 RSS를 매체 사이트로 한정해 검색한다
// 제목·매체·시각·링크만 온다 (본문 없음). Finnhub 기사와 같이 저장한다
@Component
public class PressNewsClient implements NewsSource, MarketNewsSource {

    static final String SITES = "(site:reuters.com OR site:bloomberg.com OR site:cnbc.com)";
    // 시장 뉴스는 주제마다 따로 검색한다 (한 번에 100건까지라 묶으면 주제가 한쪽으로 쏠린다). 제목에 주제어가 있는 기사만
    static final List<String> MARKET_TOPICS = List.of(
            "intitle:stocks OR intitle:\"Wall Street\"", "intitle:Fed OR intitle:Treasury", "intitle:oil",
            "intitle:dollar", "intitle:tariff OR intitle:tariffs");

    private final GoogleNewsClient google;

    public PressNewsClient(GoogleNewsClient google) {
        this.google = google;
    }

    @Override
    public boolean supports(FollowedStock stock) {
        return !stock.isKorean();
    }

    @Override
    public List<NewsItem> fetch(FollowedStock stock) {
        return google.search(query(stock) + " " + SITES + " when:3d", "en-US", "US", "US:en");
    }

    // 제목에 회사 이름이 있는 기사만. 기사는 "NVIDIA Corp"이 아니라 "Nvidia"라고 쓰므로 법인 꼬리를 뗀다
    // 티커로는 찾지 않는다: MU는 로이터 뮌헨 거래소 시세 페이지(.MU)가, 이름만 본문에 스친 기사가 대부분 걸렸다
    // ponytail: 꼬리 목록은 흔한 것만. 이름이 기사 표기와 다른 종목(예: Alphabet ↔ Google)은 덜 걸린다
    static String query(FollowedStock stock) {
        String name = stock.getName().strip();
        String before;
        do { // "Micron Technology Inc"처럼 꼬리가 여러 개일 수 있어 안 바뀔 때까지 뗀다
            before = name;
            name = name.replaceAll("(?i)[,.]?\\s+(inc|corp|corporation|co|company|ltd|plc|holdings|group|technology|technologies|class [a-c])\\.?$", "");
        } while (!name.equals(before));
        return "intitle:\"" + name + "\"";
    }

    @Override
    public List<NewsItem> fetchMarket() {
        List<NewsItem> items = new ArrayList<>();
        for (String topic : MARKET_TOPICS) {
            items.addAll(google.search("(" + topic + ") " + SITES + " when:1d", "en-US", "US", "US:en"));
        }
        return items;
    }
}
