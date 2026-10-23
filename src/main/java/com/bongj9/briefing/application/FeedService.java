package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.DisclosureRepository;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsRepository;
import com.bongj9.briefing.domain.PriceMoveRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 팔로우 종목의 공시와 뉴스를 시간순 하나의 목록으로 합친다
@Service
public class FeedService {

    private static final int MAX_ITEMS = 300;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FollowedStockRepository stockRepository;
    private final NewsRepository newsRepository;
    private final DisclosureRepository disclosureRepository;
    private final PriceMoveRepository priceMoveRepository;
    private final DisclosureSource disclosureSource;

    public FeedService(FollowedStockRepository stockRepository, NewsRepository newsRepository,
                   DisclosureRepository disclosureRepository, PriceMoveRepository priceMoveRepository,
                   DisclosureSource disclosureSource) {
        this.disclosureSource = disclosureSource;
        this.stockRepository = stockRepository;
        this.newsRepository = newsRepository;
        this.disclosureRepository = disclosureRepository;
        this.priceMoveRepository = priceMoveRepository;
    }

    // stockCode나 industry로 거른다 (둘 다 null이면 전체 + 시장 뉴스)
    public List<FeedItem> feed(String stockCode, String industry, int days) {
        boolean home = stockCode == null && industry == null;
        Map<String, String> names = stockRepository.findAll().stream()
                .filter(s -> stockCode == null || s.getStockCode().equals(stockCode))
                .filter(s -> industry == null || industry.equals(s.getIndustry()))
                .collect(Collectors.toMap(FollowedStock::getStockCode, FollowedStock::getName));
        Instant since = Instant.now().minus(Duration.ofDays(days));
        // 시장 뉴스는 하루 수백 건이라 종목 항목을 밀어내지 않도록 따로 자른다
        List<FeedItem> market = home
                ? latest(newsRepository.findByStockCodeInAndPublishedAtAfter(List.of(News.MARKET), since).stream()
                        .map(n -> new FeedItem("MARKET", null, "시장", n.getSource(), n.getTitle(), n.getSummary(), n.getLink(), n.getPublishedAt())))
                : List.of();
        if (names.isEmpty()) {
            return market;
        }
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

        return Stream.concat(latest(Stream.of(moves, news, disclosures).flatMap(s -> s)).stream(), market.stream()).toList();
    }

    private static List<FeedItem> latest(Stream<FeedItem> items) {
        return items.sorted(Comparator.comparing(FeedItem::at, Comparator.reverseOrder()))
                .limit(MAX_ITEMS)
                .toList();
    }

    // 종목 페이지 공시 탭: 최근 3개월 공시를 DART에서 바로 받는다 (저장하지 않음)
    public DisclosureList companyDisclosures(String stockCode, int offset, int limit) {
        LocalDate today = LocalDate.now(SEOUL);
        DisclosureSource.Page page = disclosureSource.fetchCompany(stockCode, today.minusMonths(3), today, offset, limit);
        // ponytail: DART 목록엔 접수 시각이 없어 접수일 0시로 둔다
        return new DisclosureList(page.items().stream()
                .map(d -> new FeedItem("DISCLOSURE", d.stockCode(), d.corpName(), "DART", d.reportNm(),
                        d.flrNm() + " 제출", d.url(), d.rceptDt().atStartOfDay(SEOUL).toInstant()))
                .toList(), page.hasMore());
    }

    public record DisclosureList(List<FeedItem> items, boolean hasMore) {
    }

    public record FeedItem(String type, String stockCode, String stockName, String source, String title, String summary, String url, Instant at) {
    }
}
