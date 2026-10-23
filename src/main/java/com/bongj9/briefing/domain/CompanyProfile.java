package com.bongj9.briefing.domain;

// 회사명·업종. 없는 티커면 name이 null
public record CompanyProfile(String name, String industry) {
}
