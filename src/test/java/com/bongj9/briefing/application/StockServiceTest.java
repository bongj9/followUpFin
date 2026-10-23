package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.CompanyProfiles;
import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.CompanyProfile;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.Holding;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceTest {

    FollowedStockRepository repository = mock(FollowedStockRepository.class);
    CompanyProfiles profiles = mock(CompanyProfiles.class);
    MarketData market = mock(MarketData.class);
    StockService service = new StockService(repository, mock(NewsCollector.class), profiles, market,
            mock(ApplicationEventPublisher.class));

    @Test
    void 보유_종목을_가져오되_이미_있는_건_건너뛰고_미국_종목은_영어_이름을_쓴다() {
        when(market.holdings()).thenReturn(List.of(
                holding("005930", "삼성전자", "KRW"),
                holding("NVDA", "엔비디아", "USD"), // 토스는 한글 이름을 준다
                holding("AAPL", "애플", "USD"), // 이미 팔로우 중
                holding("ZZZZ", "없는종목", "USD")));
        when(repository.existsById("AAPL")).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(profiles.isConfigured()).thenReturn(true);
        when(profiles.profile("NVDA")).thenReturn(new CompanyProfile("NVIDIA Corp", "Semiconductors"));
        when(profiles.profile("ZZZZ")).thenReturn(new CompanyProfile(null, null));

        StockService.ImportResult result = service.importHoldings();

        assertThat(result.added()).containsExactly("삼성전자", "엔비디아");
        assertThat(result.failed()).singleElement().asString().startsWith("없는종목");
        org.mockito.ArgumentCaptor<FollowedStock> saved = org.mockito.ArgumentCaptor.forClass(FollowedStock.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(FollowedStock::getName).containsExactly("삼성전자", "NVIDIA Corp");
    }

    private static Holding holding(String symbol, String name, String currency) {
        return new Holding(symbol, name, BigDecimal.ONE, BigDecimal.TEN, currency);
    }
}
