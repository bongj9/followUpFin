package com.bongj9.briefing.quote;

import com.bongj9.briefing.stock.FollowedStock;
import com.bongj9.briefing.stock.FollowedStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuoteService {

    private final TossClient toss;
    private final FollowedStockRepository stockRepository;
    private final BigDecimal bigMovePercent;

    QuoteService(TossClient toss, FollowedStockRepository stockRepository,
                    @Value("${briefing.big-move-percent}") BigDecimal bigMovePercent) {
        this.toss = toss;
        this.stockRepository = stockRepository;
        this.bigMovePercent = bigMovePercent;
    }

    // 팔로우 종목 현재가 + 전일 대비 등락률
    // ponytail: 요청마다 토스 호출 (현재가 1회 + 종목당 일봉 1회). 종목이 20개를 넘으면 일봉 초당 한도(20회)에 걸리니 전일 종가를 하루 캐시
    public List<Quote> quotes() {
        List<String> codes = stockRepository.findAll().stream().map(FollowedStock::getStockCode).toList();
        if (!toss.isConfigured() || codes.isEmpty()) {
            return List.of();
        }
        Map<String, TossClient.Price> prices = toss.prices(codes).stream()
                .collect(Collectors.toMap(TossClient.Price::symbol, Function.identity()));
        return codes.stream()
                .filter(prices::containsKey)
                .map(code -> toQuote(prices.get(code)))
                .toList();
    }

    private Quote toQuote(TossClient.Price price) {
        BigDecimal last = new BigDecimal(price.lastPrice());
        BigDecimal prevClose = prevClose(price.symbol());
        BigDecimal rate = Quote.changeRate(last, prevClose);
        return new Quote(price.symbol(), last, prevClose, Quote.change(last, prevClose), rate, price.currency(), price.timestamp(),
                Quote.isBigMove(rate, bigMovePercent));
    }

    // 최근 일봉 2개 중 이전 봉의 종가. 장 시작 전에는 어제 봉이 최신이라 그제 대비가 되는데, 토스 앱도 같은 방식으로 보여준다
    private BigDecimal prevClose(String symbol) {
        List<TossClient.Candle> candles = toss.dailyCandles(symbol, 2).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TossClient.Candle::timestamp).reversed())
                .toList();
        return candles.size() < 2 ? null : new BigDecimal(candles.get(1).closePrice());
    }
}
