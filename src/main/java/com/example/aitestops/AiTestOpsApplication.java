package com.example.aitestops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

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

    private final Environment environment;

    public AiTestOpsApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(AiTestOpsApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default";

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    AI-TestOps 启动成功                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  项目地址:  http://localhost:" + port + "/                         ║");
        System.out.println("║  Swagger:   http://localhost:" + port + "/swagger-ui.html        ║");
        System.out.println("║  Knife4j:   http://localhost:" + port + "/doc.html               ║");
        System.out.println("║  Profile:   " + profile + "                                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
