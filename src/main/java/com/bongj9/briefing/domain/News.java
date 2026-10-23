package com.bongj9.briefing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"stockCode", "link"}),
        indexes = @Index(columnList = "stockCode, publishedAt"))
public class News {

    public static final String MARKET = "MARKET"; // 시장 뉴스의 stockCode (종목코드와 겹치지 않음)
    private static final int SUMMARY_MAX = 1000;

    @Id
    @GeneratedValue
    private Long id;
    private String stockCode;
    @Column(length = 500)
    private String title;
    @Column(length = SUMMARY_MAX)
    private String summary;
    private String source; // 매체명 (출처가 주지 않으면 null)
    @Column(length = 2000) // 구글 뉴스 링크는 500자를 넘는다. ddl-auto는 길이를 안 바꾸니 기존 DB는 ALTER 필요
    private String link; // 같은 기사가 다른 종목에도 걸릴 수 있어 (종목, 링크)로 유일
    private Instant publishedAt;
    private Instant collectedAt;
    @Column(length = 500)
    private String titleKo; // 번역 (누른 기사만, NewsTranslator)
    @Column(length = SUMMARY_MAX * 2) // 한국어 번역이 원문보다 길 수 있다
    private String summaryKo;

    protected News() {
    }

    public News(String stockCode, NewsItem item, Instant collectedAt) {
        this.stockCode = stockCode;
        this.title = item.title();
        this.summary = item.summary() == null || item.summary().length() <= SUMMARY_MAX
                ? item.summary()
                : item.summary().substring(0, SUMMARY_MAX);
        this.source = item.source();
        this.link = item.url();
        this.publishedAt = item.publishedAt();
        this.collectedAt = collectedAt;
    }

    public Long getId() { return id; }
    public String getStockCode() { return stockCode; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getSource() { return source; }
    public String getLink() { return link; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCollectedAt() { return collectedAt; }
    public String getTitleKo() { return titleKo; }
    public String getSummaryKo() { return summaryKo; }

    // 한글이 없는 제목이면 영어 기사 (미국 종목 기사, 시장 뉴스 중 로이터·블룸버그·CNBC)
    public boolean isEnglish() {
        return !title.matches("(?s).*[가-힣].*");
    }

    public void translate(String titleKo, String summaryKo) {
        this.titleKo = titleKo;
        this.summaryKo = summaryKo;
    }
}
