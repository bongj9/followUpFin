package com.bongj9.briefing.application;

import com.bongj9.briefing.application.port.Translator;
import com.bongj9.briefing.domain.News;
import com.bongj9.briefing.domain.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 영어 기사를 누를 때만 번역하고 저장한다. 미국 기사는 하루 수백 건이라 모두 번역하면 DeepL 무료 한도(월 50만 자)를 넘는다
@Service
public class NewsTranslator {

    private final Translator translator;
    private final NewsRepository repository;

    public NewsTranslator(Translator translator, NewsRepository repository) {
        this.translator = translator;
        this.repository = repository;
    }

    @Transactional
    public News translate(long newsId) {
        News news = repository.findById(newsId).orElseThrow(() -> new IllegalArgumentException("없는 기사: " + newsId));
        if (news.getTitleKo() != null || !news.isEnglish()) {
            return news; // 이미 번역했거나 한국어 기사
        }
        if (!translator.isConfigured()) {
            throw new IllegalStateException("번역하려면 secret.yml에 deepl.api-key가 필요해요");
        }
        List<String> texts = new ArrayList<>(List.of(news.getTitle()));
        if (news.getSummary() != null && !news.getSummary().isBlank()) {
            texts.add(news.getSummary());
        }
        List<String> ko = translator.toKorean(texts);
        news.translate(ko.get(0), ko.size() > 1 ? ko.get(1) : null);
        return news;
    }
}
