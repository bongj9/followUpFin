package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.Disclosure;
import com.bongj9.briefing.domain.DisclosureItem;
import com.bongj9.briefing.domain.DisclosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DisclosureCollector {

    private final DisclosureSource source;
    private final DisclosureRepository repository;

    public DisclosureCollector(DisclosureSource source, DisclosureRepository repository) {
        this.source = source;
        this.repository = repository;
    }

    // 새 공시만 저장하고 저장 건수를 돌려준다
    @Transactional
    public int collect(LocalDate date) {
        // 수집 중 새 공시가 올라오면 페이지가 밀려 같은 공시가 두 번 올 수 있어 접수번호로 한 번 더 거른다
        Map<String, DisclosureItem> byRceptNo = new LinkedHashMap<>();
        for (DisclosureItem item : source.fetch(date)) {
            byRceptNo.putIfAbsent(item.rceptNo(), item);
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
