package com.bongj9.briefing.adapter.persistence;

import com.bongj9.briefing.application.port.TickRepository;
import com.bongj9.briefing.domain.Quote;
import com.bongj9.briefing.domain.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class TickStore implements TickRepository {

    private static final Logger log = LoggerFactory.getLogger(TickStore.class);
    private static final int RECENT = 1000;
    private static final int MAX_PENDING = 200_000; // DB가 멈췄을 때 메모리에 들고 있을 최대 체결 수 (약 수십 MB)

    private final JdbcTemplate jdbc;
    private final Queue<Quote> pending = new ConcurrentLinkedQueue<>();

    TickStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 시각을 못 읽는 체결은 여기서 버린다 (배치 하나가 통째로 실패하지 않게)
    @Override
    public void add(Quote quote) {
        try {
            OffsetDateTime.parse(quote.timestamp());
        } catch (RuntimeException e) {
            log.warn("시각을 읽을 수 없는 체결 무시 [{}]: {}", quote.stockCode(), quote.timestamp());
            return;
        }
        pending.add(quote);
    }

    // DB가 잠깐 멈추면 다음 주기에 다시 넣는다. 너무 오래 멈춰 MAX_PENDING을 넘으면 그때부터 버린다
    // ponytail: 앱이 갑자기 죽으면 메모리에 있던 체결은 잃는다. 하나도 잃으면 안 되면 디스크 큐(카프카 등)를 앞에 둘 것
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
        } catch (RuntimeException e) {
            if (pending.size() + batch.size() <= MAX_PENDING) {
                pending.addAll(batch);
                log.error("체결 {}건 저장 실패, 다음에 다시 시도: {}", batch.size(), e.getMessage());
            } else {
                log.error("체결 {}건 저장 실패, 대기열이 가득 차서 버림: {}", batch.size(), e.getMessage());
            }
        }
    }

    // 최근 체결 1000건 (오래된 순). 재시작해도 DB에 남아 있다
    @Override
    public List<Trade> recent(String symbol) {
        List<Trade> newestFirst = jdbc.query(
                "select time, price, volume, currency from trade_tick where symbol = ? order by time desc limit " + RECENT,
                (rs, i) -> new Trade(rs.getBigDecimal("price"), rs.getBigDecimal("volume"),
                        rs.getObject("time", OffsetDateTime.class).toString(), rs.getString("currency")),
                symbol);
        return newestFirst.reversed();
    }
}
