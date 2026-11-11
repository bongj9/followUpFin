package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.Llm;
import com.bongj9.briefing.domain.Disclosure;
import com.bongj9.briefing.domain.DisclosureGuide;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsRepository;
import com.bongj9.briefing.domain.DisclosureRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// 급등락 항목을 누르면: 그 시점 앞뒤의 종목 뉴스·공시·시장 뉴스 제목을 모아 Claude에게 원인을 묻는다
// 번역처럼 누를 때만 부른다 (API 크레딧은 쓴 만큼 차감)
@Service
public class MoveExplainer {

    static final Duration BEFORE = Duration.ofHours(24); // 전날 장 마감 뒤 기사까지
    static final Duration AFTER = Duration.ofHours(1);   // 감지 직후 바로 나온 기사까지
    static final int MAX_STOCK_NEWS = 30;
    static final int MAX_MARKET_NEWS = 15;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String SYSTEM = """
            너는 개인 투자자에게 주가 급등락의 원인을 설명한다.
            주어진 뉴스·공시 제목만 근거로 쓴다. 제목에 없는 사실은 지어내지 않는다.
            한국어로, 주식 초보도 알아듣게 3~4문장으로 쓴다. 근거로 삼은 제목의 번호를 [1]처럼 붙인다.
            종목 소식으로 설명이 안 되고 시장 전체가 움직였으면 그렇다고 쓴다.
            원인을 찾을 수 없으면 "제목만으로는 원인을 찾지 못했어요"라고 쓰고 이유를 한 문장 덧붙인다.
            사라·팔아라 같은 투자 권유는 하지 않는다. 마크다운 없이 평문으로 쓴다.""";

    private final Llm llm;
    private final NewsRepository newsRepository;
    private final DisclosureRepository disclosureRepository;
    // ponytail: 같은 급등락은 한 번만 묻는다. 메모리라 재시작하면 다시 묻는다. 오래 켜 두며 쌓이면 DB 저장으로
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MoveExplainer(Llm llm, NewsRepository newsRepository, DisclosureRepository disclosureRepository) {
        this.llm = llm;
        this.newsRepository = newsRepository;
        this.disclosureRepository = disclosureRepository;
    }

    // move는 피드에 보이는 급등락 제목 (예: "삼성전자 전일 대비 +7.30% 급등"), at은 감지 시각
    public String explain(String stockCode, String move, Instant at) {
        String key = stockCode + "|" + at;
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!llm.isConfigured()) {
            throw new IllegalStateException("원인 분석을 하려면 secret.yml에 anthropic.api-key가 필요해요");
        }
        Instant from = at.minus(BEFORE);
        List<News> stockNews = around(newsRepository.findByStockCodeInAndPublishedAtAfter(List.of(stockCode), from), at, MAX_STOCK_NEWS);
        List<News> marketNews = around(newsRepository.findByStockCodeInAndPublishedAtAfter(List.of(News.MARKET), from), at, MAX_MARKET_NEWS);
        LocalDate day = LocalDate.ofInstant(at, SEOUL);
        List<Disclosure> disclosures = disclosureRepository.findByStockCodeInAndRceptDtGreaterThanEqual(List.of(stockCode), day.minusDays(1)).stream()
                .filter(d -> !d.getRceptDt().isAfter(day))
                .filter(d -> DisclosureGuide.of(d.getReportNm()).importance() != DisclosureGuide.Importance.LOW)
                .toList();

        String answer = llm.ask(SYSTEM, prompt(move, at, stockNews, disclosures, marketNews));
        cache.put(key, answer);
        return answer;
    }

    // 감지 시각 앞 BEFORE ~ 뒤 AFTER 사이 기사를 최신순으로 limit건
    static List<News> around(List<News> news, Instant at, int limit) {
        Instant to = at.plus(AFTER);
        return news.stream()
                .filter(n -> !n.getPublishedAt().isAfter(to))
                .sorted(Comparator.comparing(News::getPublishedAt).reversed())
                .limit(limit)
                .toList();
    }

    static String prompt(String move, Instant at, List<News> stockNews, List<Disclosure> disclosures, List<News> marketNews) {
        int[] n = {0};
        StringBuilder sb = new StringBuilder();
        sb.append("급등락: ").append(move).append(" (감지 ").append(at.atZone(SEOUL).toLocalDateTime()).append(" 한국 시간)\n\n");
        sb.append("## 종목 공시\n").append(lines(disclosures.stream()
                .map(d -> d.getRceptDt() + " " + DisclosureGuide.of(d.getReportNm()).name() + " (" + d.getReportNm() + ")").toList(), n));
        sb.append("\n## 종목 뉴스\n").append(lines(stockNews.stream().map(MoveExplainer::headline).toList(), n));
        sb.append("\n## 시장 뉴스\n").append(lines(marketNews.stream().map(MoveExplainer::headline).toList(), n));
        return sb.toString();
    }

    private static String headline(News news) {
        return news.getPublishedAt().atZone(SEOUL).toLocalDateTime() + " " + news.getTitle();
    }

    // 번호를 세 목록에 이어서 붙인다 ([1] 공시, [2] 뉴스 …)
    private static String lines(List<String> items, int[] n) {
        if (items.isEmpty()) {
            return "(없음)\n";
        }
        return items.stream().map(s -> "[" + (++n[0]) + "] " + s).collect(Collectors.joining("\n", "", "\n"));
    }
}
