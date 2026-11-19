package com.bongj9.briefing.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTimeoutsTest {

    @Test
    void 서버가_응답하지_않으면_기다리다_실패한다() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) { // 연결만 받고 아무 말도 안 하는 서버
            RestClient client = RestClient.builder()
                    .requestFactory(HttpTimeouts.factory(Duration.ofMillis(200)))
                    .build();

            assertThatThrownBy(() -> client.get().uri("http://localhost:" + silent.getLocalPort()).retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
        }
    }
}
