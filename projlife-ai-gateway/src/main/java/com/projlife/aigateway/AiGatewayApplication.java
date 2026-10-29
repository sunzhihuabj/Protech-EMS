package com.projlife.aigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 网关（独立进程，00 册 C-02）：唯一能访问内网模型服务器的组件（安全隔离边界，13 册）。
 * 端口 8090；mock 模式恒可用；真实模型不可用时自动降级，业务方拿到 DEGRADED 响应继续流程。
 */
@SpringBootApplication
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
