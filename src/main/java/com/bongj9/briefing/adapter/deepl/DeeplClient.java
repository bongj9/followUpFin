package com.bongj9.briefing.adapter.deepl;

import com.bongj9.briefing.application.port.Translator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// DeepL 번역. 무료 키(끝이 :fx)는 api-free 주소를 쓰고 월 50만 자까지 무료
@Component
public class DeeplClient implements Translator {

    private final RestClient restClient;
    private final String apiKey;

    public DeeplClient(@Value("${deepl.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiKey.endsWith(":fx") ? "https://api-free.deepl.com/v2" : "https://api.deepl.com/v2")
                .defaultHeader("Authorization", "DeepL-Auth-Key " + apiKey)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public List<String> toKorean(List<String> texts) {
        Response res = restClient.post()
                .uri("/translate")
                .body(Map.of("text", texts, "source_lang", "EN", "target_lang", "KO"))
                .retrieve()
                .body(Response.class);
        return res.translations().stream().map(Translation::text).toList();
    }

    record Response(List<Translation> translations) {
    }

    record Translation(String text) {
    }
}
