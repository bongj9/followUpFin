package com.bongj9.briefing.quote;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

// 토스증권 Open API. 국내(005930)·미국(AAPL) 시세를 같은 API로 조회한다
// 사용 전 토스증권 WTS > 설정 > Open API 에서 client_id/secret 발급 + 허용 IP 등록 필요
@Component
public class TossClient {

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

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    // 최대 200종목 한 번에
    public List<Price> prices(Collection<String> symbols) {
        return call(token -> restClient.get()
                .uri(u -> u.path("/api/v1/prices").queryParam("symbols", String.join(",", symbols)).build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(PricesResponse.class)
                .result());
    }

    // 일봉. 최신 봉부터 온다고 문서에 있지만 정렬은 호출하는 쪽에서 다시 한다
    public List<Candle> dailyCandles(String symbol, int count) {
        return candles(symbol, "1d", count);
    }

    private List<Candle> candles(String symbol, String interval, int count) {
        return call(token -> restClient.get()
                .uri(u -> u.path("/api/v1/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("count", count)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(CandlesResponse.class)
                .result()
                .candles());
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
    public synchronized String token() {
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

    public synchronized void invalidateToken() {
        expiresAt = Instant.EPOCH;
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }

    record PricesResponse(List<Price> result) {
    }

    // 가격은 문자열로 온다 ("72300", "189.25")
    public record Price(String symbol, String timestamp, String lastPrice, String currency) {
    }

    record CandlesResponse(CandlePage result) {
    }

    record CandlePage(List<Candle> candles) {
    }

    public record Candle(String timestamp, String openPrice, String highPrice, String lowPrice, String closePrice,
                         String volume) {
    }
}
