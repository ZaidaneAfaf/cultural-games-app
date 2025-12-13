package com.example.projets5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OllamaConfig {
    @Value("${ollama.host}") private String host;
    @Bean public RestTemplate restTemplate(){ return new RestTemplate(); }
    @Bean public String ollamaHost(){ return host; }
}
