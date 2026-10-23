package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

// 화면 상단 지표 띠: 국내 지수, 미국 지수 대신 ETF, 원/달러
@Service
public class IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);
    private static final Duration TTL = Duration.ofMinutes(1); // 브라우저마다 1분마다 부르므로 토스 호출은 서버에서 한 번만
    private static final int SPARK = 120; // 작은 그래프에 쓸 1분봉 수 (최근 2시간)

    // 토스 Open API에 미국 지수·선물이 없어 같은 지수를 따라가는 ETF로 대신한다
    private static final List<Item> ITEMS = List.of(
            new Item("KOSPI", "코스피", true),
            new Item("KOSDAQ", "코스닥", true),
            new Item("QQQ", "나스닥100 (QQQ)", false),
            new Item("IWM", "러셀2000 (IWM)", false),
            new Item("SPY", "S&P500 (SPY)", false));

    private final MarketData market;
    private List<Indicator> cached = List.of();
    private Instant cachedAt = Instant.EPOCH;

    public IndicatorService(MarketData market) {
        this.market = market;
    }

    public synchronized List<Indicator> indicators() {
        if (!market.isConfigured()) {
            return List.of();
        }
        if (cachedAt.plus(TTL).isAfter(Instant.now())) {
            return cached;
        }
        List<Indicator> result = new ArrayList<>();
        for (Item item : ITEMS) {
            try {
                Function<String, List<Candle>> minute = item.index()
                        ? s -> market.indicatorCandles(s, "1m", SPARK) : s -> market.minuteCandles(s, SPARK);
                Function<String, List<Candle>> daily = item.index()
                        ? s -> market.indicatorCandles(s, "1d", 2) : s -> market.dailyCandles(s, 2);
                result.add(toIndicator(item.label(), minute.apply(item.symbol()), daily.apply(item.symbol())));
            } catch (RuntimeException e) {
                log.warn("지표 조회 실패 [{}]: {}", item.symbol(), e.getMessage()); // 하나가 실패해도 나머지는 보여준다
            }
        }
        try {
            // ponytail: 환율은 일봉이 없어 24시간 전 매매기준율과 비교하고 그래프는 없다
            BigDecimal now = market.usdKrw(null);
            BigDecimal dayAgo = market.usdKrw(Instant.now().minus(Duration.ofDays(1)));
            result.add(new Indicator("원/달러", now, Quote.changeRate(now, dayAgo), List.of()));
        } catch (RuntimeException e) {
            log.warn("환율 조회 실패: {}", e.getMessage());
        }
        cached = result;
        cachedAt = Instant.now();
        return result;
    }

    // 현재값은 마지막 1분봉 종가, 전일 종가는 일봉 2개 중 이전 봉 (QuoteService와 같은 기준)
    static Indicator toIndicator(String label, List<Candle> minutes, List<Candle> days) {
        List<BigDecimal> spark = minutes.stream()
                .sorted(Comparator.comparing(Candle::time))
                .map(Candle::close)
                .toList();
        BigDecimal last = spark.isEmpty() ? null : spark.getLast();
        List<Candle> recentFirst = days.stream().sorted(Comparator.comparing(Candle::time).reversed()).toList();
        BigDecimal prevClose = recentFirst.size() < 2 ? null : recentFirst.get(1).close();
        return new Indicator(label, last, last == null ? null : Quote.changeRate(last, prevClose), spark);
    }

    private record Item(String symbol, String label, boolean index) {
    }

    public record Indicator(String label, BigDecimal last, BigDecimal changeRate, List<BigDecimal> spark) {
    }
}
