package com.bongj9.briefing.live;

import com.bongj9.briefing.quote.Quote;
import com.bongj9.briefing.quote.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"briefing.scheduling=false", "spring.datasource.url=jdbc:h2:mem:ticks"})
class TickStoreTest {

    @MockitoBean
    QuoteService quoteService;

    @Autowired
    TickStore store;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 모았다가_한_번에_저장하고_오래된_순으로_돌려준다() {
        // 실제로는 db/init/001-trade-tick.sql이 TimescaleDB에 만드는 테이블. H2 numeric은 소수를 버려서 decfloat로 대신한다
        jdbc.execute("create table trade_tick (time timestamp with time zone not null, symbol varchar not null, "
                + "price decfloat not null, volume decfloat not null, currency varchar not null)");
        when(quoteService.toQuote(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(a -> tick(a.getArgument(1).toString(), a.getArgument(3)));

        store.add(tick("100.5", "2026-10-22T22:30:01+09:00"));
        store.add(tick("100.7", "2026-10-22T22:30:02+09:00"));
        store.flush();

        assertThat(store.recent("NVDA")).extracting(q -> q.lastPrice().toPlainString()).containsExactly("100.5", "100.7");
    }

    private static Quote tick(String price, String timestamp) {
        return new Quote("NVDA", new BigDecimal(price), null, null, null, "USD", timestamp, false, BigDecimal.ONE);
    }
}
