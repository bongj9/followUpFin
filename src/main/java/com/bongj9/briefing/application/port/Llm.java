package com.bongj9.briefing.application.port;

// 글을 읽고 답을 쓰는 언어 모델 (지금은 Claude)
public interface Llm {

    boolean isConfigured(); // 키가 없으면 false

    // system은 역할·규칙, prompt는 이번 질문. 답은 글 한 덩어리
    String ask(String system, String prompt);
}
