package com.bongj9.briefing.live;

import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.TossClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongUnaryOperator;

@RestController
class LiveController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long KST_SECONDS = 9 * 3600;
    private static final Map<String, Integer> MINUTES = Map.of("1m", 1, "5m", 5, "15m", 15, "30m", 30, "1h", 60);
    // 일봉 페이지 수 (200일씩): 주봉 약 2년, 월봉 약 8년, 년봉 최대 약 24년
    private static final Map<String, Integer> DAY_PAGES = Map.of("1d", 1, "1w", 3, "1M", 10, "1y", 30);
    private static final int MAX_PAGES = 10;

    private final LiveBroadcaster broadcaster;
    private final TickBuffer ticks;
    private final TossClient toss;

    LiveController(LiveBroadcaster broadcaster, TickBuffer ticks, TossClient toss) {
        this.broadcaster = broadcaster;
        this.ticks = ticks;
        this.toss = toss;
    }

    // 실시간 체결(trade)·큰 변동(move) 스트림
    @GetMapping("/api/stream")
    SseEmitter stream() {
        return broadcaster.connect();
    }

    // 틱 차트 초기 데이터: 서버가 켜진 뒤 받은 최근 체결 (최대 1000건)
    @GetMapping("/api/ticks")
    List<Quote> ticks(@RequestParam String symbol) {
        return ticks.recent(symbol.toUpperCase());
    }

    // 캔들 차트 초기 데이터 (오래된 순). time은 차트에 그대로 쓰도록 한국 시간으로 옮긴 epoch 초
    // 토스는 1m·1d만 있어서 5분 이상은 1분봉을, 주·월·년은 일봉을 이어 받아 합친다
    @GetMapping("/api/candles")
    List<Bar> candles(@RequestParam String symbol, @RequestParam(defaultValue = "1m") String interval) {
        String code = symbol.toUpperCase();
        Integer dayPages = DAY_PAGES.get(interval);
        if (dayPages != null) {
            List<Bar> days = fetch(code, "1d", dayPages).stream()
                    .map(c -> bar(OffsetDateTime.parse(c.timestamp()).atZoneSameInstant(KST).toLocalDate()
                            .toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC), c))
                    .toList();
            return merge(days, t -> periodStart(interval, LocalDate.ofEpochDay(t / 86400))
                    .toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC));
        }
        Integer minutes = MINUTES.get(interval);
        if (minutes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원 주기: " + MINUTES.keySet() + ", " + DAY_PAGES.keySet());
        }
        // ponytail: 1분봉 최대 2000개(10번 호출)까지만 합친다. 1시간봉이면 30여 개. 더 길게 보려면 캐시를 두고 늘릴 것
        List<Bar> mins = fetch(code, "1m", Math.min(minutes, MAX_PAGES)).stream()
                .map(c -> bar(OffsetDateTime.parse(c.timestamp()).toEpochSecond() - 60 + KST_SECONDS, c)) // 끝 시각 → 시작 시각
                .toList();
        long size = minutes * 60L;
        return merge(mins, t -> t - Math.floorMod(t, size));
    }

    // before/nextBefore로 과거 페이지를 이어 받는다 (페이지당 200개)
    private List<TossClient.Candle> fetch(String code, String interval, int pages) {
        List<TossClient.Candle> raw = new ArrayList<>();
        String before = null;
        for (int page = 0; page < pages; page++) {
            TossClient.CandlePage result = toss.candles(code, interval, 200, before);
            raw.addAll(result.candles());
            before = result.nextBefore();
            if (before == null || result.candles().isEmpty()) {
                break;
            }
        }
        return raw;
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

    private static Bar bar(long time, TossClient.Candle c) {
        return new Bar(time, new BigDecimal(c.openPrice()), new BigDecimal(c.highPrice()),
                new BigDecimal(c.lowPrice()), new BigDecimal(c.closePrice()), new BigDecimal(c.volume()));
    }

    record Bar(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
    }
}
