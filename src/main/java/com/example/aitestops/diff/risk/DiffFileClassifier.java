package com.example.aitestops.diff.risk;

import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class DiffFileClassifier {

    public DiffFileRoleEnum classify(String path) {
        if (!StringUtils.hasText(path)) {
            return DiffFileRoleEnum.OTHER;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("test")) return DiffFileRoleEnum.TEST;
        if (lower.contains("auth") || lower.contains("security") || lower.contains("permission")) return DiffFileRoleEnum.AUTH;
        if (lower.contains("controller")) return DiffFileRoleEnum.CONTROLLER;
        if (lower.contains("service")) return DiffFileRoleEnum.SERVICE;
        if (lower.contains("mapper") || lower.contains("dao") || lower.contains("repository")) return DiffFileRoleEnum.DAO;
        if (lower.endsWith(".sql")) return DiffFileRoleEnum.SQL;
        if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) return DiffFileRoleEnum.CONFIG;
        if (lower.contains("job") || lower.contains("scheduler")) return DiffFileRoleEnum.JOB;
        if (lower.contains("mq") || lower.contains("consumer") || lower.contains("producer")) return DiffFileRoleEnum.MQ;
        return DiffFileRoleEnum.OTHER;
    }

    public boolean isTestFile(String path) {
        return classify(path) == DiffFileRoleEnum.TEST;
    }
}
