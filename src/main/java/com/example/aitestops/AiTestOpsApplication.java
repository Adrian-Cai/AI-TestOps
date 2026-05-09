package com.example.aitestops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 测试设计 Demo 后端启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan({
        "com.example.aitestops.document.mapper",
        "com.example.aitestops.ai.mapper",
        "com.example.aitestops.testcase.mapper",
        "com.example.aitestops.review.mapper",
        "com.example.aitestops.diff.mapper"
})
public class AiTestOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTestOpsApplication.class, args);
    }
}
