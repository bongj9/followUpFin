package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.CompanyProfiles;
import com.bongj9.briefing.application.port.MarketData;
import com.bongj9.briefing.domain.CompanyProfile;
import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.FollowedStocksChanged;
import com.bongj9.briefing.domain.Holding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final FollowedStockRepository repository;
    private final NewsCollector newsCollector;
    private final CompanyProfiles profiles;
    private final MarketData market;
    private final ApplicationEventPublisher events;

    public StockService(FollowedStockRepository repository, NewsCollector newsCollector, CompanyProfiles profiles,
                        MarketData market, ApplicationEventPublisher events) {
        this.repository = repository;
        this.newsCollector = newsCollector;
        this.profiles = profiles;
        this.market = market;
        this.events = events;
    }

    // 증권 계좌에 들고 있는 종목 중 아직 팔로우하지 않은 것을 추가한다
    // 미국 종목은 토스 이름(한글) 대신 Finnhub 영어 이름을 쓴다: 로이터·블룸버그 기사를 제목의 회사 이름으로 찾기 때문
    public ImportResult importHoldings() {
        List<String> added = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Holding h : market.holdings()) {
            if (repository.existsById(h.symbol())) {
                continue;
            }
            try {
                boolean keepName = FollowedStock.isKoreanCode(h.symbol()) || FollowedStock.etfUnderlying(h.symbol()).isPresent();
                follow(h.symbol(), keepName ? h.name() : "");
                added.add(h.name());
            } catch (InvalidStockException e) {
                failed.add(h.name() + " (" + e.getMessage() + ")");
            }
        }
        return new ImportResult(added, failed);
    }

    public List<Holding> holdings() {
        return market.holdings();
    }

    public record ImportResult(List<String> added, List<String> failed) {
    }

    public List<FollowedStock> list() {
        return repository.findAll();
    }

    // 입력이 잘못됐으면 InvalidStockException (화면에 그대로 보여줄 메시지)
    public FollowedStock follow(String stockCode, String name) {
        String code = stockCode == null ? "" : stockCode.strip().toUpperCase();
        if (!code.matches("\\d{6}|[A-Z]{1,5}(\\.[A-Z])?")) {
            throw new InvalidStockException("국내 종목코드 6자리 또는 미국 티커(AAPL)");
        }
        name = name == null ? "" : name.strip();
        String industry = null;
        // 레버리지 ETF는 Finnhub 무료 프로필에 없어서 이름을 안 주면 티커 + 기초 종목으로 둔다
        Optional<FollowedStock> underlying = FollowedStock.etfUnderlying(code);
        if (underlying.isPresent() && name.isEmpty()) {
            name = code + " (" + underlying.get().getName() + " 레버리지)";
        }
        // 미국 종목은 티커만 받으면 회사명·업종을 채운다
        if (!FollowedStock.isKoreanCode(code) && underlying.isEmpty() && profiles.isConfigured()) {
            CompanyProfile profile = profiles.profile(code);
            if (profile.name() == null) {
                throw new InvalidStockException("Finnhub에 없는 티커: " + code);
            }
            name = name.isEmpty() ? profile.name() : name;
            industry = profile.industry();
        }
        if (name.isEmpty() || name.length() > 100) {
            throw new InvalidStockException("종목명을 입력하세요");
        }
        FollowedStock stock = repository.save(new FollowedStock(code, name, industry));
        events.publishEvent(new FollowedStocksChanged());
        // 다음 수집 주기까지 기다리지 않도록 바로 한 번 수집
        try {
            newsCollector.collect(stock);
        } catch (RuntimeException e) {
            log.warn("{} 첫 뉴스 수집 실패: {}", stock.getName(), e.getMessage());
        }
        return stock;
    }

    public void unfollow(String stockCode) {
        repository.deleteById(stockCode);
        events.publishEvent(new FollowedStocksChanged());
    }

    public static class InvalidStockException extends RuntimeException {
        InvalidStockException(String message) {
            super(message);
        }
    }
}
