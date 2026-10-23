package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.LastPrice;
import com.bongj9.briefing.domain.Quote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuoteService {

    // 전일 종가는 하루에 한 번 바뀌지만 바뀌는 시각(토스 일봉 경계)이 자정이 아니라서 날짜 대신 짧은 TTL로 캐시
    private static final Duration PREV_CLOSE_TTL = Duration.ofMinutes(10);

    private final MarketData market;
    private final FollowedStockRepository stockRepository;
    private final BigDecimal bigMovePercent;
    private final Map<String, CachedClose> prevCloses = new ConcurrentHashMap<>();

    public QuoteService(MarketData market, FollowedStockRepository stockRepository,
                 @Value("${briefing.big-move-percent}") BigDecimal bigMovePercent) {
        this.market = market;
        this.stockRepository = stockRepository;
        this.bigMovePercent = bigMovePercent;
    }

    // 팔로우 종목 현재가 + 전일 대비 등락률 (현재가 1회 호출, 전일 종가는 캐시)
    public List<Quote> quotes() {
        List<String> codes = stockRepository.findAll().stream().map(FollowedStock::getStockCode).toList();
        if (!market.isConfigured() || codes.isEmpty()) {
            return List.of();
        }
        Map<String, LastPrice> prices = market.prices(codes).stream()
                .collect(Collectors.toMap(LastPrice::symbol, Function.identity()));
        return codes.stream()
                .filter(prices::containsKey)
                .map(code -> {
                    LastPrice p = prices.get(code);
                    return toQuote(p.symbol(), p.price(), p.currency(), p.timestamp(), null);
                })
                .toList();
    }

    // 웹소켓 체결 한 건을 시세로 변환
    public Quote toQuote(String symbol, BigDecimal last, String currency, String timestamp, BigDecimal volume) {
        BigDecimal prevClose = prevClose(symbol);
        BigDecimal rate = Quote.changeRate(last, prevClose);
        return new Quote(symbol, last, prevClose, Quote.change(last, prevClose), rate, currency, timestamp,
                Quote.isBigMove(rate, bigMovePercent), volume);
    }

    private BigDecimal prevClose(String symbol) {
        CachedClose cached = prevCloses.get(symbol);
        if (cached != null && cached.fetchedAt().plus(PREV_CLOSE_TTL).isAfter(Instant.now())) {
            return cached.value();
        }
        BigDecimal value = fetchPrevClose(symbol);
        prevCloses.put(symbol, new CachedClose(value, Instant.now()));
        return value;
    }

    // 최근 일봉 2개 중 이전 봉의 종가. 장 시작 전에는 어제 봉이 최신이라 그제 대비가 되는데, 토스 앱도 같은 방식으로 보여준다
    private BigDecimal fetchPrevClose(String symbol) {
        List<Candle> candles = market.dailyCandles(symbol, 2).stream()
                .sorted(Comparator.comparing(Candle::time).reversed())
                .toList();
        return candles.size() < 2 ? null : candles.get(1).close();
    }

    private record CachedClose(BigDecimal value, Instant fetchedAt) {
    }
}
