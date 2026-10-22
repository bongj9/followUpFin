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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
class LiveController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long KST_SECONDS = 9 * 3600;
    private static final Map<String, Integer> MINUTES = Map.of("1m", 1, "5m", 5, "15m", 15, "30m", 30, "1h", 60);
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
    // 토스는 1m·1d만 있어서 5분 이상은 1분봉을 받아 합친다
    @GetMapping("/api/candles")
    List<Bar> candles(@RequestParam String symbol, @RequestParam(defaultValue = "1m") String interval) {
        String code = symbol.toUpperCase();
        if (interval.equals("1d")) {
            return toss.candles(code, "1d", 200, null).candles().stream()
                    .map(c -> bar(OffsetDateTime.parse(c.timestamp()).atZoneSameInstant(KST).toLocalDate()
                            .toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC), c))
                    .sorted(Comparator.comparingLong(Bar::time))
                    .toList();
        }
        Integer minutes = MINUTES.get(interval);
        if (minutes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원 주기: " + MINUTES.keySet() + ", 1d");
        }
        // ponytail: 1분봉 최대 2000개(10번 호출)까지만 합친다. 1시간봉이면 30여 개. 더 길게 보려면 캐시를 두고 늘릴 것
        List<TossClient.Candle> raw = new ArrayList<>();
        String before = null;
        for (int page = 0; page < Math.min(minutes, MAX_PAGES); page++) {
            TossClient.CandlePage result = toss.candles(code, "1m", 200, before);
            raw.addAll(result.candles());
            before = result.nextBefore();
            if (before == null || result.candles().isEmpty()) {
                break;
            }
        }
        long size = minutes * 60L;
        TreeMap<Long, Bar> buckets = new TreeMap<>();
        raw.stream()
                .map(c -> bar(OffsetDateTime.parse(c.timestamp()).toEpochSecond() - 60 + KST_SECONDS, c)) // 끝 시각 → 시작 시각
                .sorted(Comparator.comparingLong(Bar::time))
                .forEach(b -> buckets.merge(b.time() - Math.floorMod(b.time(), size),
                        new Bar(b.time() - Math.floorMod(b.time(), size), b.open(), b.high(), b.low(), b.close(), b.volume()),
                        (a, n) -> new Bar(a.time(), a.open(), a.high().max(n.high()), a.low().min(n.low()), n.close(),
                                a.volume().add(n.volume()))));
        return List.copyOf(buckets.values());
    }

    private static Bar bar(long time, TossClient.Candle c) {
        return new Bar(time, new BigDecimal(c.openPrice()), new BigDecimal(c.highPrice()),
                new BigDecimal(c.lowPrice()), new BigDecimal(c.closePrice()), new BigDecimal(c.volume()));
    }

    record Bar(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
    }
}
