package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.LastPrice;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Trade;

import java.util.Collection;
import java.util.List;

// 시세 출처 (지금은 토스증권). 증권사를 바꾸면 이 인터페이스의 구현만 새로 만든다
public interface MarketData {

    boolean isConfigured(); // 키가 없으면 false

    List<LastPrice> prices(Collection<String> symbols);

    // 최신 봉부터 최대 count개
    List<Candle> minuteCandles(String symbol, int count);

    List<Candle> dailyCandles(String symbol, int count);

    Orderbook orderbook(String symbol);

    // 당일 최근 체결 (최신순)
    List<Trade> trades(String symbol);
}
