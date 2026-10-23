package com.bongj9.briefing.adapter.toss;

import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.InvestorFlow;
import com.bongj9.briefing.domain.LastPrice;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Trade;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// 토스증권 Open API. 국내(005930)·미국(AAPL) 시세를 같은 API로 조회한다
// 사용 전 토스증권 WTS > 설정 > Open API 에서 client_id/secret 발급 + 허용 IP 등록 필요
// 토스 응답(가격이 문자열인 Toss* 레코드)을 받아 우리 도메인 타입으로 바꿔서 돌려준다
@Component
public class TossClient implements MarketData {

    private static final int CANDLE_PAGE = 200; // 토스 최대값

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://openapi.tossinvest.com")
            .build();
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public TossClient(@Value("${toss.client-id:}") String clientId,
                      @Value("${toss.client-secret:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    // 최대 200종목 한 번에
    @Override
    public List<LastPrice> prices(Collection<String> symbols) {
        return call(token -> restClient.get()
                .uri(u -> u.path("/api/v1/prices").queryParam("symbols", String.join(",", symbols)).build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(PricesResponse.class)
                .result()).stream()
                .map(p -> new LastPrice(p.symbol(), new BigDecimal(p.lastPrice()), p.currency(), p.timestamp()))
                .toList();
    }

    @Override
    public List<Candle> minuteCandles(String symbol, int count) {
        return candles(symbol, "1m", count);
    }

    // 최신 봉부터 온다고 문서에 있지만 정렬은 호출하는 쪽에서 다시 한다
    @Override
    public List<Candle> dailyCandles(String symbol, int count) {
        return candles(symbol, "1d", count);
    }

    // 토스는 interval 1m·1d만 지원하고 한 번에 200개까지. before(포함)를 주면 그 시각부터 과거로, 다음 페이지는 nextBefore로 이어 받는다
    private List<Candle> candles(String symbol, String interval, int count) {
        List<Candle> candles = new ArrayList<>();
        String before = null;
        // 페이지 수로 끊는다: 빈 봉이 섞여 와도 더 과거 봉을 끌어오지 않게 (전일 종가가 그제 종가로 바뀌는 걸 막음)
        for (int fetched = 0; fetched < count; fetched += CANDLE_PAGE) {
            String from = before;
            int size = Math.min(CANDLE_PAGE, count - fetched);
            TossCandlePage page = call(token -> restClient.get()
                    .uri(u -> u.path("/api/v1/candles")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("count", size)
                            .queryParamIfPresent("before", Optional.ofNullable(from))
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(CandlesResponse.class)
                    .result());
            page.candles().stream().map(TossCandle::toCandleOrNull).filter(c -> c != null).forEach(candles::add);
            before = page.nextBefore();
            if (before == null || page.candles().isEmpty()) {
                break;
            }
        }
        return candles;
    }

    // 호가 (매도는 낮은 가격순, 매수는 높은 가격순)
    @Override
    public Orderbook orderbook(String symbol) {
        return call(token -> restClient.get()
                .uri(u -> u.path("/api/v1/orderbook").queryParam("symbol", symbol).build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(OrderbookResponse.class)
                .result()).toOrderbook();
    }

    // 당일 최근 체결 (최대 50건, 최신순)
    @Override
    public List<Trade> trades(String symbol) {
        return call(token -> restClient.get()
                .uri(u -> u.path("/api/v1/trades").queryParam("symbol", symbol).queryParam("count", 50).build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(TradesResponse.class)
                .result()).stream()
                .map(t -> new Trade(new BigDecimal(t.price()), new BigDecimal(t.volume()), t.timestamp(), t.currency()))
                .toList();
    }

    // 투자자별 매매동향 (국내만, 해외 티커는 400 unsupported-market). 최대 100일
    @Override
    public List<InvestorFlow> investorFlows(String symbol, int count) {
        return call(token -> restClient.get()
                .uri(u -> u.path("/api/v1/stocks/{symbol}/investor-trading").queryParam("count", count).build(symbol))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(InvestorTradingResponse.class)
                .result()
                .records()).stream()
                .map(TossInvestorRecord::toFlow)
                .toList();
    }

    // 다른 곳(curl 테스트 등)에서 토큰을 새로 받으면 기존 토큰이 무효가 되므로 401이면 한 번 재발급 후 재시도
    private <T> T call(Function<String, T> request) {
        try {
            return request.apply(token());
        } catch (HttpClientErrorException.Unauthorized e) {
            invalidateToken();
            return request.apply(token());
        }
    }

    // 클라이언트당 유효 토큰은 1개, refresh token 없음. 만료 1분 전까지 재사용 (웹소켓 연결도 같은 토큰)
    synchronized String token() {
        if (Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        TokenResponse res = restClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        accessToken = res.accessToken();
        expiresAt = Instant.now().plusSeconds(res.expiresIn());
        return accessToken;
    }

    synchronized void invalidateToken() {
        expiresAt = Instant.EPOCH;
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }

    // 아래는 토스 응답 모양 그대로. 가격·수량은 문자열로 온다 ("72300", "189.25")
    record PricesResponse(List<TossPrice> result) {
    }

    record TossPrice(String symbol, String timestamp, String lastPrice, String currency) {
    }

    record CandlesResponse(TossCandlePage result) {
    }

    record TossCandlePage(List<TossCandle> candles, String nextBefore) {
    }

    record TossCandle(String timestamp, String openPrice, String highPrice, String lowPrice, String closePrice,
                      String volume) {

        // 비어 있는 봉(아직 체결 없는 일봉 등)은 null로 버린다. 봉 하나 때문에 시세 전체가 실패하지 않게
        static Candle toCandleOrNull(TossCandle c) {
            try {
                return c == null ? null : c.toCandle();
            } catch (RuntimeException e) {
                return null;
            }
        }

        Candle toCandle() {
            return new Candle(OffsetDateTime.parse(timestamp).toInstant(), new BigDecimal(openPrice),
                    new BigDecimal(highPrice), new BigDecimal(lowPrice), new BigDecimal(closePrice),
                    new BigDecimal(volume));
        }
    }

    record OrderbookResponse(TossOrderbook result) {
    }

    // 웹소켓 호가 프레임의 data도 같은 모양이다
    record TossOrderbook(String timestamp, String currency, List<TossLevel> asks, List<TossLevel> bids) {

        Orderbook toOrderbook() {
            return new Orderbook(timestamp, currency, levels(asks), levels(bids));
        }

        private static List<Orderbook.Level> levels(List<TossLevel> levels) {
            return levels == null ? List.of() : levels.stream()
                    .map(l -> new Orderbook.Level(new BigDecimal(l.price()), new BigDecimal(l.volume())))
                    .toList();
        }
    }

    record TossLevel(String price, String volume) {
    }

    record TradesResponse(List<TossTrade> result) {
    }

    record TossTrade(String price, String volume, String timestamp, String currency) {
    }

    record InvestorTradingResponse(TossInvestorPage result) {
    }

    record TossInvestorPage(List<TossInvestorRecord> records, String nextUntil) {
    }

    // 매수·매도 수량도 오지만 화면엔 순매수만 쓴다. 기관 세부(breakdown)·CFD는 받지 않는다
    record TossInvestorRecord(String date, TossNet individual, TossNet foreigner, TossNet institution,
                              TossNet otherCorporation, TossHolding foreignerHolding) {

        InvestorFlow toFlow() {
            return new InvestorFlow(LocalDate.parse(date), TossNet.net(individual), TossNet.net(foreigner),
                    TossNet.net(institution), TossNet.net(otherCorporation),
                    foreignerHolding == null || foreignerHolding.holdingRate() == null ? null
                            : new BigDecimal(foreignerHolding.holdingRate()));
        }
    }

    record TossNet(String netBuyVolume) {

        static Long net(TossNet n) {
            return n == null || n.netBuyVolume() == null ? null : Long.valueOf(n.netBuyVolume());
        }
    }

    record TossHolding(String holdingRate) {
    }
}
