package com.bongj9.briefing.live;

import com.bongj9.briefing.quote.Quote;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 종목별 최근 체결. 틱 차트를 열었을 때 빈 화면 대신 직전 체결부터 보여주려고 메모리에만 둔다
@Component
class TickBuffer {

    private static final int SIZE = 1000;

    private final Map<String, Deque<Quote>> ticks = new ConcurrentHashMap<>();

    void add(Quote quote) {
        Deque<Quote> deque = ticks.computeIfAbsent(quote.stockCode(), k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(quote);
            if (deque.size() > SIZE) {
                deque.removeFirst();
            }
        }
    }

    List<Quote> recent(String symbol) {
        Deque<Quote> deque = ticks.get(symbol);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }
}
