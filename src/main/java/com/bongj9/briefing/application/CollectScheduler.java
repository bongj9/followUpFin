package com.bongj9.briefing.application;

import com.bongj9.briefing.domain.FollowedStock;
import com.bongj9.briefing.domain.FollowedStockRepository;
import com.bongj9.briefing.domain.PriceMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(name = "briefing.scheduling", havingValue = "true", matchIfMissing = true)
class CollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectScheduler.class);

    private final DisclosureCollector disclosureCollector;
    private final NewsCollector newsCollector;
    private final FollowedStockRepository stockRepository;
    private final PriceMoveDetector priceMoveDetector;

    CollectScheduler(DisclosureCollector disclosureCollector, NewsCollector newsCollector,
                     FollowedStockRepository stockRepository, PriceMoveDetector priceMoveDetector) {
        this.disclosureCollector = disclosureCollector;
        this.newsCollector = newsCollector;
        this.stockRepository = stockRepository;
        this.priceMoveDetector = priceMoveDetector;
    }

    // 수집원 하나가 실패해도 나머지는 계속 돈다
    @Scheduled(fixedDelayString = "${briefing.poll-interval}")
    void run() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        try {
            // ponytail: 자정 직전 공시를 놓치지 않도록 어제도 같이 확인. 호출 수 2배(하루 약 3천 건, 한도 2만)
            int saved = disclosureCollector.collect(today.minusDays(1)) + disclosureCollector.collect(today);
            log.info("공시 수집: 신규 {}건", saved);
        } catch (RuntimeException e) {
            log.error("공시 수집 실패: {}", e.getMessage());
        }

        for (FollowedStock stock : stockRepository.findAll()) {
            try {
                log.info("뉴스 수집 [{}]: 신규 {}건", stock.getName(), newsCollector.collect(stock));
            } catch (RuntimeException e) {
                log.error("뉴스 수집 실패 [{}]: {}", stock.getName(), e.getMessage());
            }
        }

        // 뉴스를 먼저 모은 뒤 감지해야 급등락 시점의 기사가 피드에 같이 있다
        try {
            for (PriceMove move : priceMoveDetector.detect()) {
                log.info("큰 변동 감지 [{}]: {}%", move.getStockCode(), move.getChangeRate());
            }
        } catch (RuntimeException e) {
            log.error("시세 변동 감지 실패: {}", e.getMessage());
        }
    }
}
