package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.IntradayMove;
import com.bongj9.briefing.domain.IntradayMoveRepository;
import com.bongj9.briefing.domain.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 장중 급등락: 최근 5분 안에 3% 이상 움직이면 기록한다. 평소에 많이 움직이는 종목은 평소 5분 변동의 4배가 더 크면 그걸 기준으로 쓴다
// 3%는 Webull "Sharp Rise/Fall" 알림(5분 3%)과 같은 선. 거래소 정지선(미국 LULD 5분 5%·10%, 한국 VI)보다 먼저 알린다
// 고정 기준(예: 1분 3%)은 대형주는 거의 못 잡고 거래 적은 소형주·프리마켓은 너무 자주 잡아서 종목마다 기준을 둔다
// 가격은 메모리에만 1초에 하나씩 5분치 들고 있는다 (체결은 저장하지 않는다)
@Service
public class IntradayMoveDetector {

    private static final Logger log = LoggerFactory.getLogger(IntradayMoveDetector.class);
    static final Duration WINDOW = Duration.ofMinutes(5);
    static final Duration COOLDOWN = Duration.ofMinutes(30); // 같은 종목 같은 방향은 30분 동안 다시 알리지 않는다
    static final double MULTIPLE = 4;
    static final double MIN_PERCENT = 3;
    private static final int CANDLES = 200;

    private final MarketData market;
    private final IntradayMoveRepository repository;
    private final FollowedStockRepository stockRepository;
    private final Map<String, Double> normalRates = new ConcurrentHashMap<>(); // 종목 → 평소 5분 변동 %
    private final Map<String, Deque<Tick>> windows = new HashMap<>();
    private final Map<String, Instant> lastAlerts = new HashMap<>(); // "NVDA+" → 마지막 알림 시각

    public IntradayMoveDetector(MarketData market, IntradayMoveRepository repository,
                                FollowedStockRepository stockRepository) {
        this.market = market;
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    // 평소 변동을 최근 1분봉으로 다시 구한다 (수집 주기마다). 새로 팔로우한 종목은 다음 주기부터 감지한다
    public void refresh() {
        for (FollowedStock stock : stockRepository.findAll()) {
            try {
                double normal = normalRate(market.minuteCandles(stock.getStockCode(), CANDLES));
                normalRates.put(stock.getStockCode(), normal);
                log.info("장중 급등락 기준 [{}]: 평소 5분 ±{}% → {}% 이상", stock.getStockCode(),
                        String.format("%.2f", normal), String.format("%.2f", Math.max(MIN_PERCENT, MULTIPLE * normal)));
            } catch (RuntimeException e) {
                log.warn("평소 변동 계산 실패 [{}]: {}", stock.getStockCode(), e.getMessage());
            }
        }
    }

    // 평소 5분 변동 = 최근 1분봉에서 잰 5분 등락률 절댓값의 평균. 가장 크게 튄 10%는 뺀다 (급등락 자체가 기준을 끌어올리지 않게)
    // 이어진 봉끼리만 본다: 장 사이 공백이나 밤사이 갭은 평소 움직임이 아니다. 잴 구간이 너무 적으면 NaN(감지 안 함)
    // 1분 등락의 중앙값은 쓰지 않는다: 호가 단위가 큰 국내 대형주는 1분봉 대부분이 0%라 중앙값이 0이 된다
    static double normalRate(List<Candle> newestFirst) {
        int span = (int) WINDOW.toMinutes();
        List<Double> moves = new ArrayList<>();
        for (int i = 0; i + span < newestFirst.size(); i++) {
            Candle now = newestFirst.get(i);
            Candle before = newestFirst.get(i + span);
            if (Duration.between(before.time(), now.time()).equals(WINDOW) && before.close().signum() > 0) {
                moves.add(Math.abs(now.close().doubleValue() / before.close().doubleValue() - 1) * 100);
            }
        }
        if (moves.size() < 30) {
            return Double.NaN;
        }
        Collections.sort(moves);
        return moves.subList(0, moves.size() * 9 / 10).stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    public Optional<IntradayMove> check(Quote quote) {
        return check(quote, Instant.now());
    }

    synchronized Optional<IntradayMove> check(Quote quote, Instant now) {
        String code = quote.stockCode();
        BigDecimal price = quote.lastPrice();
        Deque<Tick> window = windows.computeIfAbsent(code, k -> new ArrayDeque<>());
        if (!window.isEmpty() && window.peekLast().at().getEpochSecond() == now.getEpochSecond()) {
            window.pollLast(); // 같은 초의 체결은 마지막 가격만
        }
        window.addLast(new Tick(now, price));
        while (window.peekFirst().at().isBefore(now.minus(WINDOW))) {
            window.pollFirst();
        }

        Double normal = normalRates.get(code);
        if (normal == null || normal.isNaN()) {
            return Optional.empty();
        }
        BigDecimal low = window.stream().map(Tick::price).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal high = window.stream().map(Tick::price).max(BigDecimal::compareTo).orElseThrow();
        double rise = (price.doubleValue() / low.doubleValue() - 1) * 100;
        double fall = (price.doubleValue() / high.doubleValue() - 1) * 100;
        double move = rise >= -fall ? rise : fall;
        if (Math.abs(move) < Math.max(MIN_PERCENT, MULTIPLE * normal)) {
            return Optional.empty();
        }
        String key = code + (move > 0 ? "+" : "-");
        Instant last = lastAlerts.get(key);
        if (last != null && now.isBefore(last.plus(COOLDOWN))) {
            return Optional.empty();
        }
        lastAlerts.put(key, now);
        return Optional.of(repository.save(new IntradayMove(code, move > 0 ? low : high, price,
                BigDecimal.valueOf(move).setScale(2, RoundingMode.HALF_UP), (int) WINDOW.toMinutes(),
                BigDecimal.valueOf(normal).setScale(2, RoundingMode.HALF_UP), quote.currency(), now)));
    }

    private record Tick(Instant at, BigDecimal price) {
    }
}
