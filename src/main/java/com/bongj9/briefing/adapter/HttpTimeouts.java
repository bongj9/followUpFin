package com.bongj9.briefing.adapter;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

// 외부 API가 응답을 안 주면 기본값(무한 대기)으로는 수집 작업이 통째로 멈춘다
public final class HttpTimeouts {

    private HttpTimeouts() {
    }

    public static SimpleClientHttpRequestFactory factory() {
        return factory(Duration.ofSeconds(30));
    }

    // read는 전체 시간이 아니라 데이터가 끊긴 시간이라, 큰 파일(DART 회사코드 zip)을 받아도 걸리지 않는다
    static SimpleClientHttpRequestFactory factory(Duration read) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(read);
        return f;
    }
}
