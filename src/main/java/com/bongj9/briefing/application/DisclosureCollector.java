package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.Disclosure;
import com.bongj9.briefing.domain.DisclosureItem;
import com.bongj9.briefing.domain.DisclosureRepository;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DisclosureCollector {

    private static final Logger log = LoggerFactory.getLogger(DisclosureCollector.class);

    private final List<DisclosureSource> sources; // 스프링이 DisclosureSource 구현 빈을 전부 넣어 준다 (DART, EDGAR)
    private final DisclosureRepository repository;
    private final FollowedStockRepository stockRepository;

    public DisclosureCollector(List<DisclosureSource> sources, DisclosureRepository repository,
                               FollowedStockRepository stockRepository) {
        this.sources = sources;
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    // 새 공시만 저장하고 저장 건수를 돌려준다. 출처 하나가 실패해도 나머지는 저장한다
    @Transactional
    public int collect(LocalDate date) {
        List<String> codes = stockRepository.findAll().stream().map(FollowedStock::getStockCode).toList();
        // 수집 중 새 공시가 올라오면 페이지가 밀려 같은 공시가 두 번 올 수 있어 접수번호로 한 번 더 거른다
        Map<String, DisclosureItem> byRceptNo = new LinkedHashMap<>();
        for (DisclosureSource source : sources) {
            try {
                for (DisclosureItem item : source.fetch(date, codes.stream().filter(source::supports).toList())) {
                    byRceptNo.putIfAbsent(item.rceptNo(), item);
                }
            } catch (RuntimeException e) {
                log.error("공시 수집 실패 [{}]: {}", source.getClass().getSimpleName(), e.getMessage());
            }
        }
        repository.findAllById(byRceptNo.keySet())
                .forEach(saved -> byRceptNo.remove(saved.getRceptNo()));

        Instant now = Instant.now();
        List<Disclosure> fresh = byRceptNo.values().stream()
                .map(item -> new Disclosure(item, now))
                .toList();
        repository.saveAll(fresh);
        return fresh.size();
    }
}
