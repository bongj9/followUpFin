package com.bongj9.briefing.feed;

import com.bongj9.briefing.disclosure.DisclosureRepository;
import com.bongj9.briefing.move.PriceMoveRepository;
import com.bongj9.briefing.news.NewsRepository;
import com.bongj9.briefing.stock.FollowedStock;
import com.bongj9.briefing.stock.FollowedStockRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 팔로우 종목의 공시와 뉴스를 시간순 하나의 목록으로 합친다
@RestController
class FeedController {

    private static final int MAX_ITEMS = 300;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FollowedStockRepository stockRepository;
    private final NewsRepository newsRepository;
    private final DisclosureRepository disclosureRepository;
    private final PriceMoveRepository priceMoveRepository;

    FeedController(FollowedStockRepository stockRepository, NewsRepository newsRepository,
                   DisclosureRepository disclosureRepository, PriceMoveRepository priceMoveRepository) {
        this.stockRepository = stockRepository;
        this.newsRepository = newsRepository;
        this.disclosureRepository = disclosureRepository;
        this.priceMoveRepository = priceMoveRepository;
    }

    // GET /api/feed?stockCode=AAPL 또는 ?industry=Semiconductors (둘 다 없으면 전체)
    @GetMapping("/api/feed")
    List<FeedItem> feed(@RequestParam(required = false) String stockCode,
                        @RequestParam(required = false) String industry,
                        @RequestParam(defaultValue = "3") int days) {
        Map<String, String> names = stockRepository.findAll().stream()
                .filter(s -> stockCode == null || s.getStockCode().equals(stockCode))
                .filter(s -> industry == null || industry.equals(s.getIndustry()))
                .collect(Collectors.toMap(FollowedStock::getStockCode, FollowedStock::getName));
        if (names.isEmpty()) {
            return List.of();
        }
        Instant since = Instant.now().minus(Duration.ofDays(days));
        LocalDate sinceDate = LocalDate.ofInstant(since, SEOUL);

        Stream<FeedItem> news = newsRepository.findByStockCodeInAndPublishedAtAfter(names.keySet(), since).stream()
                .map(n -> new FeedItem("NEWS", n.getStockCode(), names.get(n.getStockCode()),
                        n.getSource(), n.getTitle(), n.getSummary(), n.getLink(), n.getPublishedAt()));
        // ponytail: DART 목록엔 접수 시각이 없어 수집 시각으로 정렬. 첫 수집분은 모두 같은 시각
        Stream<FeedItem> disclosures = disclosureRepository.findByStockCodeInAndRceptDtGreaterThanEqual(names.keySet(), sinceDate).stream()
                .map(d -> new FeedItem("DISCLOSURE", d.getStockCode(), names.get(d.getStockCode()),
                        "DART", d.getReportNm(), d.getFlrNm() + " 제출", d.getUrl(), d.getCollectedAt()));

        Stream<FeedItem> moves = priceMoveRepository.findByStockCodeInAndDetectedAtAfter(names.keySet(), since).stream()
                .map(m -> new FeedItem("MOVE", m.getStockCode(), names.get(m.getStockCode()), "토스증권",
                        String.format("%s 전일 대비 %+.2f%% %s", names.get(m.getStockCode()), m.getChangeRate(),
                                m.getChangeRate().signum() > 0 ? "급등" : "급락"),
                        "현재가 " + m.getLastPrice().toPlainString() + " " + m.getCurrency() + " · 눌러서 관련 뉴스 보기",
                        null, m.getDetectedAt()));

        return Stream.of(moves, news, disclosures).flatMap(s -> s)
                .sorted(Comparator.comparing(FeedItem::at, Comparator.reverseOrder()))
                .limit(MAX_ITEMS)
                .toList();
    }

    record FeedItem(String type, String stockCode, String stockName, String source, String title, String summary, String url, Instant at) {
    }
}
