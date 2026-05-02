package com.example.aitestops.common.config;

import org.apache.tika.parser.AutoDetectParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Apache Tika 配置，集中创建自动识别解析器。
 */
@Configuration
public class TikaConfig {

    @Bean
    public AutoDetectParser autoDetectParser() {
        return new AutoDetectParser();
    }
}
