package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.DisclosureGuide;
import com.bongj9.briefing.domain.DisclosureItem;
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
import java.util.ArrayList;
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
    private final List<DisclosureSource> disclosureSources;

    public FeedService(FollowedStockRepository stockRepository, NewsRepository newsRepository,
                   DisclosureRepository disclosureRepository, PriceMoveRepository priceMoveRepository,
                   List<DisclosureSource> disclosureSources) {
        this.disclosureSources = disclosureSources;
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
        // ponytail: DART 목록엔 접수 시각이 없어 수집 시각으로 정렬. 첫 수집분은 모두 같은 시각 (SEC는 접수 시각)
        Stream<FeedItem> disclosures = disclosureRepository.findByStockCodeInAndRceptDtGreaterThanEqual(names.keySet(), sinceDate).stream()
                .map(d -> FeedItem.disclosure(d.getStockCode(), names.get(d.getStockCode()), d.getReportNm(), d.getFlrNm(),
                        d.getUrl(), d.getAt()))
                .filter(item -> !item.minor()); // 임원 지분 보고·펀드 서류 같은 사소한 공시는 피드에서 뺀다

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

    // 종목 페이지 공시 탭: 최근 3개월 공시를 출처(DART, EDGAR)에서 바로 받는다 (저장하지 않음)
    // minor가 false면 사소한 공시를 건너뛰고 limit건을 채운다. nextOffset은 출처 목록에서 이어 받을 위치 (없으면 null)
    // ponytail: 남은 공시가 전부 사소한 것이어도 nextOffset이 남을 수 있다. 그때 '더 보기'는 빈 결과를 받고 사라진다
    public DisclosureList companyDisclosures(String stockCode, int offset, int limit, boolean minor) {
        DisclosureSource source = disclosureSources.stream()
                .filter(s -> s.supports(stockCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("이 종목의 공시 출처가 없어요 (미국 공시는 secret.yml의 sec.contact 필요)"));
        LocalDate today = LocalDate.now(SEOUL);
        List<FeedItem> items = new ArrayList<>();
        int next = offset;
        for (int pages = 0; pages < 5; pages++) { // DART 한 페이지 100건. 대형주 3개월치도 보통 몇백 건
            DisclosureSource.Page page = source.fetchCompany(stockCode, today.minusMonths(3), today, next, 100);
            for (int i = 0; i < page.items().size(); i++) {
                DisclosureItem d = page.items().get(i);
                next++;
                // ponytail: DART 목록엔 접수 시각이 없어 접수일 0시로 둔다 (화면은 날짜만 표시)
                FeedItem item = FeedItem.disclosure(d.stockCode(), d.corpName(), d.reportNm(), d.flrNm(), d.url(),
                        d.acceptedAt() != null ? d.acceptedAt() : d.rceptDt().atStartOfDay(SEOUL).toInstant());
                if (minor || !item.minor()) {
                    items.add(item);
                }
                if (items.size() == limit) {
                    boolean rest = i < page.items().size() - 1 || page.hasMore();
                    return new DisclosureList(items, rest ? next : null);
                }
            }
            if (!page.hasMore() || page.items().isEmpty()) {
                return new DisclosureList(items, null);
            }
        }
        return new DisclosureList(items, next);
    }

    public record DisclosureList(List<FeedItem> items, Integer nextOffset) {
    }

    // importance·tip은 공시에만 있다 (DisclosureGuide)
    public record FeedItem(String type, String stockCode, String stockName, String source, String title, String summary,
                           String url, Instant at, String importance, String tip) {

        FeedItem(String type, String stockCode, String stockName, String source, String title, String summary, String url, Instant at) {
            this(type, stockCode, stockName, source, title, summary, url, at, null, null);
        }

        static FeedItem disclosure(String stockCode, String stockName, String reportNm, String flrNm, String url, Instant at) {
            DisclosureGuide guide = DisclosureGuide.of(reportNm);
            String original = guide.name().equals(reportNm) ? "" : " · " + reportNm; // 바꾼 이름이면 원래 제목도 작게
            String source = FollowedStock.isKoreanCode(stockCode) ? "DART" : "SEC";
            return new FeedItem("DISCLOSURE", stockCode, stockName, source, guide.name(), flrNm + " 제출" + original,
                    url, at, guide.importance().name(), guide.tip());
        }

        boolean minor() {
            return DisclosureGuide.Importance.LOW.name().equals(importance);
        }
    }
}
