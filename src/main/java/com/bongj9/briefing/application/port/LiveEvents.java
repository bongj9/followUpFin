package com.bongj9.briefing.application.port;

// 화면으로 실시간 이벤트 보내기 (SSE)
public interface LiveEvents {

    void send(String event, Object data);
}
