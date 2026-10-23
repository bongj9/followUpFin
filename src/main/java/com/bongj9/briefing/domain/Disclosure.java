package com.bongj9.briefing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(indexes = @Index(columnList = "rceptDt, stockCode"))
public class Disclosure {

    @Id
    private String rceptNo; // DART 접수번호·SEC accession number = 공시 고유값, 중복 저장 방지 기준
    private String corpCode; // DART 회사코드, SEC는 CIK
    private String corpName;
    private String stockCode;
    private String corpCls;
    private String reportNm;
    private String flrNm;
    private LocalDate rceptDt;
    private String rm;
    private String url;
    private Instant acceptedAt; // SEC만 있음
    private Instant collectedAt;

    protected Disclosure() {
    }

    public Disclosure(DisclosureItem item, Instant collectedAt) {
        this.rceptNo = item.rceptNo();
        this.corpCode = item.corpCode();
        this.corpName = item.corpName();
        this.stockCode = item.stockCode();
        this.corpCls = item.corpCls();
        this.reportNm = item.reportNm();
        this.flrNm = item.flrNm();
        this.rceptDt = item.rceptDt();
        this.rm = item.rm();
        this.url = item.url();
        this.acceptedAt = item.acceptedAt();
        this.collectedAt = collectedAt;
    }

    public String getRceptNo() { return rceptNo; }
    public String getCorpCode() { return corpCode; }
    public String getCorpName() { return corpName; }
    public String getStockCode() { return stockCode; }
    public String getCorpCls() { return corpCls; }
    public String getReportNm() { return reportNm; }
    public String getFlrNm() { return flrNm; }
    public LocalDate getRceptDt() { return rceptDt; }
    public String getRm() { return rm; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getCollectedAt() { return collectedAt; }

    // 원문 링크. url 칸이 생기기 전에 저장된 공시는 모두 DART라 접수번호로 만든다
    public String getUrl() {
        return url != null ? url : "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + rceptNo;
    }

    // 피드 정렬 시각: 접수 시각이 없으면(DART) 수집 시각
    public Instant getAt() {
        return acceptedAt != null ? acceptedAt : collectedAt;
    }
}
