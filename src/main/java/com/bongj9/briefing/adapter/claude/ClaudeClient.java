package com.bongj9.briefing.adapter.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.bongj9.briefing.application.port.Llm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

// Claude API. 키는 console.anthropic.com에서 받은 sk-ant- 키 (Max 구독 로그인과는 별개, 쓴 만큼 API 크레딧 차감)
@Component
public class ClaudeClient implements Llm {

    private static final String MODEL = "claude-opus-5-5";

    private final AnthropicClient client; // 키가 없으면 null

    public ClaudeClient(@Value("${anthropic.api-key:}") String apiKey) {
        this.client = apiKey.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public String ask(String system, String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(system)
                .addUserMessage(prompt)
                // 헤드라인 정리라 깊게 생각할 일이 아니다. 누르고 기다리는 화면이라 빠른 쪽
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // 안전 분류기가 거절하면 서버가 알아서 다른 모델로 다시 답한다
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        BetaMessage res = client.beta().messages().create(params);
        if (res.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            throw new IllegalStateException("Claude가 이 요청에 답하지 않았어요");
        }
        return res.content().stream()
                .flatMap(b -> b.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining())
                .strip();
    }
}
