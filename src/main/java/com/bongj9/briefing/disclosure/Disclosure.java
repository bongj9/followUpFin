package com.bongj9.briefing.disclosure;

import com.bongj9.briefing.dart.DartClient;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(indexes = @Index(columnList = "rceptDt, stockCode"))
public class Disclosure {

    @Id
    private String rceptNo; // DART 접수번호 = 공시 고유값, 중복 저장 방지 기준
    private String corpCode;
    private String corpName;
    private String stockCode;
    private String corpCls;
    private String reportNm;
    private String flrNm;
    private LocalDate rceptDt;
    private String rm;
    private Instant collectedAt;

    protected Disclosure() {
    }

    public Disclosure(DartClient.Item item, Instant collectedAt) {
        this.rceptNo = item.rceptNo();
        this.corpCode = item.corpCode();
        this.corpName = item.corpName();
        this.stockCode = item.stockCode();
        this.corpCls = item.corpCls();
        this.reportNm = item.reportNm().strip().replaceAll("\\s+", " "); // DART가 공백을 여러 칸 붙여서 보냄
        this.flrNm = item.flrNm();
        this.rceptDt = LocalDate.parse(item.rceptDt(), DateTimeFormatter.BASIC_ISO_DATE);
        this.rm = item.rm();
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
    public Instant getCollectedAt() { return collectedAt; }

    // DART 원문 뷰어 링크
    public String getUrl() {
        return "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + rceptNo;
    }
}
