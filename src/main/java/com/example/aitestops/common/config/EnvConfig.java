package com.example.aitestops.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * .env 环境变量加载器。
 *
 * <p>使用 EnvironmentPostProcessor 在 Spring 配置绑定前加载 .env，
 * 确保数据库和大模型配置都能从 .env 生效。</p>
 */
public class EnvConfig implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);
    private static final String PROPERTY_SOURCE_NAME = "aiTestopsDotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String activeProfiles = environment.getProperty("spring.profiles.active", "");
        String activeProfilesEnv = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
        if (containsTestProfile(activeProfiles) || containsTestProfile(activeProfilesEnv)) {
            log.info("test profile 已启用，跳过 .env 加载");
            return;
        }
        File envFile = new File(".env");
        if (!envFile.exists()) {
            log.info(".env 文件不存在，使用系统环境变量和 application.yml 默认值");
            return;
        }

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .filename(".env")
                .ignoreIfMalformed()
                .load();

        Map<String, Object> values = new LinkedHashMap<>();
        dotenv.entries().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        log.info(".env 配置已加载: count={}", values.size());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean containsTestProfile(String profiles) {
        if (profiles == null || profiles.isBlank()) {
            return false;
        }
        for (String profile : profiles.split(",")) {
            if ("test".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
