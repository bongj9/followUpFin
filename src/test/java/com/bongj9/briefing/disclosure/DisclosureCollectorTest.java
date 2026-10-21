package com.bongj9.briefing.disclosure;

import com.bongj9.briefing.dart.DartClient;
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
    DartClient dartClient;

    @Autowired
    DisclosureCollector collector;

    @Autowired
    DisclosureRepository repository;

    @Test
    void 같은_공시는_한_번만_저장된다() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        DartClient.Item a = item("20261021000001", "005930");
        DartClient.Item b = item("20261021000002", "000660");
        when(dartClient.fetchList(day)).thenReturn(List.of(a, b, a)); // 페이지 밀림으로 a가 두 번 옴

        assertThat(collector.collect(day)).isEqualTo(2);
        assertThat(collector.collect(day)).isZero(); // 다음 주기에 같은 목록이 와도 저장 안 함
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByStockCodeInAndRceptDtGreaterThanEqual(List.of("005930"), day))
                .extracting(Disclosure::getRceptNo)
                .containsExactly("20261021000001");
    }

    private DartClient.Item item(String rceptNo, String stockCode) {
        return new DartClient.Item(rceptNo, "00000000", "회사", stockCode, "Y", "주요사항보고서   ", "회사", "20261021", "");
    }
}
