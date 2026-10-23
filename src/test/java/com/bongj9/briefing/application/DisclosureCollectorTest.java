package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.DisclosureSource;
import com.bongj9.briefing.domain.Disclosure;
import com.bongj9.briefing.domain.DisclosureItem;
import com.bongj9.briefing.domain.DisclosureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"briefing.scheduling=false", "spring.datasource.url=jdbc:h2:mem:test"})
class DisclosureCollectorTest {

    @MockitoBean
    DisclosureSource source;

    @Autowired
    DisclosureCollector collector;

    @Autowired
    DisclosureRepository repository;

    @Test
    void 같은_공시는_한_번만_저장된다() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        DisclosureItem a = item("20261021000001", "005930");
        DisclosureItem b = item("20261021000002", "000660");
        when(source.fetch(day)).thenReturn(List.of(a, b, a)); // 페이지 밀림으로 a가 두 번 옴

        assertThat(collector.collect(day)).isEqualTo(2);
        assertThat(collector.collect(day)).isZero(); // 다음 주기에 같은 목록이 와도 저장 안 함
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByStockCodeInAndRceptDtGreaterThanEqual(List.of("005930"), day))
                .extracting(Disclosure::getRceptNo)
                .containsExactly("20261021000001");
    }

    private DisclosureItem item(String rceptNo, String stockCode) {
        return new DisclosureItem(rceptNo, "00000000", "회사", stockCode, "Y", "주요사항보고서", "회사", LocalDate.of(2026, 10, 21), "");
    }
}
