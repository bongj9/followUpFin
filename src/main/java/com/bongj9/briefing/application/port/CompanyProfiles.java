package com.bongj9.briefing.application.port;

import com.bongj9.briefing.domain.CompanyProfile;

// 미국 티커 → 회사명·업종 (Finnhub)
public interface CompanyProfiles {

    boolean isConfigured();

    CompanyProfile profile(String symbol);
}
