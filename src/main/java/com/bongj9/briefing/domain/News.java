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

    private static final int SUMMARY_MAX = 1000;

    @Id
    @GeneratedValue
    private Long id;
    private String stockCode;
    @Column(length = 500)
    private String title;
    @Column(length = SUMMARY_MAX)
    private String summary;
    private String source; // 매체명 (네이버는 주지 않음)
    @Column(length = 500)
    private String link; // 같은 기사가 다른 종목에도 걸릴 수 있어 (종목, 링크)로 유일
    private Instant publishedAt;
    private Instant collectedAt;

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
}
