package com.bongj9.briefing.application.port;

import java.util.List;

// 영어 → 한국어 번역 (지금은 DeepL)
public interface Translator {

    boolean isConfigured(); // 키가 없으면 false

    // 받은 순서대로 번역문을 돌려준다
    List<String> toKorean(List<String> texts);
}
