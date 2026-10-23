package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.IntradayMove;
import com.bongj9.briefing.domain.IntradayMoveRepository;
import com.bongj9.briefing.domain.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntradayMoveDetectorTest {

    static final Instant T0 = Instant.parse("2026-10-23T14:00:00Z");

    MarketData market = mock(MarketData.class);
    IntradayMoveRepository repository = mock(IntradayMoveRepository.class);
    FollowedStockRepository stocks = mock(FollowedStockRepository.class);
    IntradayMoveDetector detector = new IntradayMoveDetector(market, repository, stocks);

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(stocks.findAll()).thenReturn(List.of(new FollowedStock("AAPL", "Apple", null)));
    }

    @Test
    void 호가_단위가_커서_1분봉이_대부분_그대로여도_평소_변동을_구한다() {
        // 10분마다 한 호가(0.5%)만 오르내리는 종목: 1분 등락의 중앙값이면 0이 된다
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            candles.add(candle(T0.minus(Duration.ofMinutes(i)), new BigDecimal((i / 10) % 2 == 0 ? "60300" : "60000")));
        }
        assertThat(IntradayMoveDetector.normalRate(candles)).isGreaterThan(0.1);
    }

    @Test
    void 평소_변동은_이어진_봉의_5분_등락_평균이고_공백과_튀는_구간은_뺀다() {
        // 1분마다 ±0.1%씩 오르내리다가 한 번 10% 튀고, 중간에 밤사이 공백(갭 20%)이 있다
        List<Candle> candles = minuteCandles(100, 0.1);
        candles.set(50, candle(candles.get(50).time(), candles.get(51).close().multiply(new BigDecimal("1.10"))));
        candles.add(candle(candles.getLast().time().minus(Duration.ofHours(12)), new BigDecimal("80")));

        double normal = IntradayMoveDetector.normalRate(candles);

        assertThat(normal).isCloseTo(0.1, within(0.01)); // 홀수 칸(5분) 떨어진 봉끼리는 늘 0.1% 차이
        assertThat(IntradayMoveDetector.normalRate(minuteCandles(10, 0.1))).isNaN(); // 봉이 너무 적으면 감지 안 함
    }

    @Test
    void 평소의_4배를_넘게_5분_안에_움직이면_한_번_기록하고_30분은_다시_알리지_않는다() {
        when(market.minuteCandles(eq("AAPL"), anyInt())).thenReturn(minuteCandles(100, 0.1)); // 평소 5분 0.1% → 기준 3%(최소)
        detector.refresh();

        assertThat(trade("100.00", 0)).isEmpty();
        assertThat(trade("102.00", 60)).isEmpty(); // +2%: 기준 미만
        Optional<IntradayMove> up = trade("103.20", 120); // 저점 100 대비 +3.2%
        assertThat(up).isPresent();
        assertThat(up.get().getChangeRate()).isEqualByComparingTo("3.20");
        assertThat(up.get().getFromPrice()).isEqualByComparingTo("100.00");

        assertThat(trade("104.00", 180)).isEmpty(); // 같은 방향은 30분 동안 다시 안 알림
        assertThat(trade("100.80", 240)).isPresent(); // 반대 방향(고점 104 대비 -3.08%)은 따로 알림
    }

    @Test
    void 평소에_많이_움직이는_종목은_기준이_높다() {
        when(market.minuteCandles(eq("AAPL"), anyInt())).thenReturn(minuteCandles(100, 1.5)); // 평소 5분 1.5% → 기준 6%
        detector.refresh();

        trade("100", 0);
        assertThat(trade("104", 60)).isEmpty(); // 4%는 이 종목에겐 크지 않다
        assertThat(trade("106.5", 120)).isPresent();
    }

    @Test
    void 창_밖으로_나간_가격은_비교하지_않는다() {
        when(market.minuteCandles(eq("AAPL"), anyInt())).thenReturn(minuteCandles(100, 0.1));
        detector.refresh();

        trade("100", 0);
        assertThat(trade("103.5", 301)).isEmpty(); // 100은 5분 넘게 지나 창에서 빠짐
    }

    private Optional<IntradayMove> trade(String price, int seconds) {
        Quote quote = new Quote("AAPL", new BigDecimal(price), null, null, null, "USD", null, false, BigDecimal.ONE);
        return detector.check(quote, T0.plusSeconds(seconds));
    }

    // 최신순 1분봉. 종가가 1분마다 percent%씩 번갈아 오르내린다
    private static List<Candle> minuteCandles(int count, double percent) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = 100 * (i % 2 == 0 ? 1 + percent / 100 : 1);
            candles.add(candle(T0.minus(Duration.ofMinutes(i)), BigDecimal.valueOf(close)));
        }
        return candles;
    }

    private static Candle candle(Instant time, BigDecimal close) {
        return new Candle(time, close, close, close, close, BigDecimal.ONE);
    }
}
