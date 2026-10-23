package com.bongj9.briefing.live;

import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// 받은 체결을 TimescaleDB trade_tick(db/init/001-trade-tick.sql)에 쌓는다
// 웹소켓 스레드를 막지 않도록 큐에 모았다가 1초마다 한 번에 넣는다
@Component
class TickStore {

    private static final Logger log = LoggerFactory.getLogger(TickStore.class);
    private static final int RECENT = 1000;

    private final JdbcTemplate jdbc;
    private final QuoteService quoteService;
    private final Queue<Quote> pending = new ConcurrentLinkedQueue<>();

    TickStore(JdbcTemplate jdbc, QuoteService quoteService) {
        this.jdbc = jdbc;
        this.quoteService = quoteService;
    }

    void add(Quote quote) {
        pending.add(quote);
    }

    // ponytail: 앱이 갑자기 죽으면 최대 1초치 체결을 잃는다. 하나도 잃으면 안 되면 디스크 큐(카프카 등)를 앞에 둘 것
    @Scheduled(fixedDelay = 1_000)
    void flush() {
        List<Quote> batch = new ArrayList<>();
        for (Quote q; (q = pending.poll()) != null; ) {
            batch.add(q);
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate("insert into trade_tick (time, symbol, price, volume, currency) values (?, ?, ?, ?, ?)",
                    batch, batch.size(), (ps, q) -> {
                        ps.setObject(1, OffsetDateTime.parse(q.timestamp()));
                        ps.setString(2, q.stockCode());
                        ps.setBigDecimal(3, q.lastPrice());
                        ps.setBigDecimal(4, q.volume());
                        ps.setString(5, q.currency());
                    });
        } catch (DataAccessException e) {
            log.error("체결 {}건 저장 실패: {}", batch.size(), e.getMessage());
        }
    }

    // 틱 차트 초기 데이터: 최근 체결 1000건 (오래된 순). 재시작해도 DB에 남아 있다
    List<Quote> recent(String symbol) {
        List<Quote> newestFirst = jdbc.query(
                "select time, price, volume, currency from trade_tick where symbol = ? order by time desc limit " + RECENT,
                (rs, i) -> quoteService.toQuote(symbol, rs.getBigDecimal("price"), rs.getString("currency"),
                        rs.getObject("time", OffsetDateTime.class).toString(), rs.getBigDecimal("volume")),
                symbol);
        return newestFirst.reversed();
    }
}
