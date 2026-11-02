package com.projlife.aigateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 网关模型调用客户端：连接超时短（不可达快速失败），读超时给足推理时间
 * （推理型 LLM 思维链常达 30~60s，读超时从 projlife.ai.timeout-ms 注入）。
 */
@Configuration
public class GatewayRestTemplateConfig {

    @Value("${projlife.ai.timeout-ms:60000}")
    private long timeoutMs;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }
}
