package com.codereviewer.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Spring AI to Groq's OpenAI-compatible endpoint.
 *
 * Groq exposes the exact same REST API as OpenAI — we just point
 * the base-url at api.groq.com and swap in our Groq key.
 * Model: llama3-70b-8192  (free tier, 6000 tokens/min)
 */
@Configuration
public class AppConfig {

    @Value("${spring.ai.openai.api-key}")
    private String groqApiKey;

    @Value("${spring.ai.openai.base-url:https://api.groq.com/openai}")
    private String groqBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:llama3-70b-8192}")
    private String model;

    /**
     * Single ChatClient shared across all agent nodes.
     * Nodes use temperature=0.1 by default (analytical/deterministic).
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel() {
        OpenAiApi api = new OpenAiApi(groqBaseUrl, groqApiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .maxTokens(2048)
                .build();
        return new OpenAiChatModel(api, options);
    }
}
