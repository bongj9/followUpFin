package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongUnaryOperator;

// 차트용 봉. 시세 출처는 1분봉·일봉만 주므로 5분 이상은 1분봉을, 주·월·년은 일봉을 합쳐 만든다
@Service
public class CandleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long KST_SECONDS = 9 * 3600;
    private static final Map<String, Integer> MINUTES = Map.of("1m", 1, "5m", 5, "15m", 15, "30m", 30, "1h", 60);
    // 받아올 일봉 수: 주봉 약 2년, 월봉 약 8년, 년봉 최대 약 24년
    private static final Map<String, Integer> DAYS = Map.of("1d", 200, "1w", 600, "1M", 2000, "1y", 6000);
    private static final int MAX_MINUTE_CANDLES = 2000;

    private final MarketData market;

    public CandleService(MarketData market) {
        this.market = market;
    }

    public boolean supports(String interval) {
        return MINUTES.containsKey(interval) || DAYS.containsKey(interval);
    }

    // 오래된 순. time은 차트에 그대로 쓰도록 한국 시간으로 옮긴 epoch 초
    public List<Bar> bars(String symbol, String interval) {
        Integer days = DAYS.get(interval);
        if (days != null) {
            List<Bar> daily = market.dailyCandles(symbol, days).stream()
                    .map(c -> bar(c.time().atZone(KST).toLocalDate().toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC), c))
                    .toList();
            return merge(daily, t -> periodStart(interval, LocalDate.ofEpochDay(t / 86400))
                    .toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC));
        }
        Integer minutes = MINUTES.get(interval);
        if (minutes == null) {
            throw new IllegalArgumentException("지원하지 않는 주기: " + interval);
        }
        // ponytail: 1분봉 최대 2000개까지만 합친다. 1시간봉이면 30여 개. 더 길게 보려면 캐시를 두고 늘릴 것
        List<Bar> mins = market.minuteCandles(symbol, Math.min(minutes * 200, MAX_MINUTE_CANDLES)).stream()
                .map(c -> bar(c.time().getEpochSecond() - 60 + KST_SECONDS, c)) // 끝 시각 → 시작 시각
                .toList();
        long size = minutes * 60L;
        return merge(mins, t -> t - Math.floorMod(t, size));
    }

    private static LocalDate periodStart(String interval, LocalDate day) {
        return switch (interval) {
            case "1w" -> day.with(DayOfWeek.MONDAY);
            case "1M" -> day.withDayOfMonth(1);
            case "1y" -> day.withDayOfYear(1);
            default -> day;
        };
    }

    // 같은 구간(bucket)의 봉을 시가·고가·저가·종가·거래량으로 합친다
    static List<Bar> merge(List<Bar> bars, LongUnaryOperator bucket) {
        TreeMap<Long, Bar> buckets = new TreeMap<>();
        bars.stream()
                .sorted(Comparator.comparingLong(Bar::time))
                .forEach(b -> {
                    long key = bucket.applyAsLong(b.time());
                    buckets.merge(key, new Bar(key, b.open(), b.high(), b.low(), b.close(), b.volume()),
                            (a, n) -> new Bar(key, a.open(), a.high().max(n.high()), a.low().min(n.low()), n.close(),
                                    a.volume().add(n.volume())));
                });
        return List.copyOf(buckets.values());
    }

    private static Bar bar(long time, Candle c) {
        return new Bar(time, c.open(), c.high(), c.low(), c.close(), c.volume());
    }

    public record Bar(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
    }
}
