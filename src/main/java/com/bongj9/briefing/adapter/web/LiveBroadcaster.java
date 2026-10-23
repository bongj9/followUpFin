package com.bongj9.briefing.adapter.web;

import com.bongj9.briefing.application.port.LiveEvents;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// 브라우저로 실시간 이벤트를 보내는 SSE 연결 목록. 데이터가 서버 → 화면 한 방향이라 웹소켓 대신 SSE
@Component
class LiveBroadcaster implements LiveEvents {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L); // 시간 제한 없음. 끊기면 브라우저 EventSource가 다시 붙는다
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @Override
    public void send(String event, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter); // 탭을 닫은 연결
            }
        }
    }
}
