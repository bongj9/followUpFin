package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.Candle;
import com.bongj9.briefing.domain.Holding;
import com.bongj9.briefing.domain.InvestorFlow;
import com.bongj9.briefing.domain.MarketFlow;
import com.bongj9.briefing.domain.LastPrice;
import com.bongj9.briefing.domain.Orderbook;
import com.bongj9.briefing.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;
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

    // 국내 종목만. 최신 날짜부터 최대 count일
    List<InvestorFlow> investorFlows(String symbol, int count);

    // 시장(KOSPI, KOSDAQ) 전체 투자자별 순매수 금액. 최신 날짜부터 count일 (일별)
    List<MarketFlow> marketFlows(String symbol, int count);

    // 시장 지표(KOSPI, KOSDAQ) 캔들. interval은 1m 또는 1d, 최대 200개
    List<Candle> indicatorCandles(String symbol, String interval, int count);

    // 원/달러 매매기준율. at이 null이면 지금
    BigDecimal usdKrw(Instant at);

    // 내 종합매매 계좌의 보유 주식 (국내·미국)
    List<Holding> holdings();
}
