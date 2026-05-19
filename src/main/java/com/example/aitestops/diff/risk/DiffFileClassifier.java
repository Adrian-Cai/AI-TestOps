package com.example.aitestops.diff.risk;

import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Diff 文件分类器。
 * <p>
 * 根据文件路径和名称识别文件在系统中的角色类型，
 * 如控制器、服务层、数据访问层、配置文件等。
 * </p>
 */
@Component
public class DiffFileClassifier {

    public DiffFileRoleEnum classify(String path) {
        if (!StringUtils.hasText(path)) {
            return DiffFileRoleEnum.OTHER;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        String fileName = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        String dirPath = lower.contains("/") ? lower.substring(0, lower.lastIndexOf('/')) : "";
        if (dirPath.contains("/test/") || dirPath.contains("/tests/") || dirPath.endsWith("/test")
                || isTestLikeFileName(fileName)) {
            return DiffFileRoleEnum.TEST;
        }
        if (fileName.contains("auth") || fileName.contains("security") || fileName.contains("permission")
                || dirPath.contains("/auth/") || dirPath.contains("/security/") || dirPath.contains("/permission/")) {
            return DiffFileRoleEnum.AUTH;
        }
        if (fileName.endsWith("controller.java") || dirPath.contains("/controller/")) return DiffFileRoleEnum.CONTROLLER;
        if (fileName.endsWith("service.java") || fileName.endsWith("serviceimpl.java") || dirPath.contains("/service/")) return DiffFileRoleEnum.SERVICE;
        if (fileName.endsWith("mapper.java") || fileName.endsWith("dao.java") || fileName.endsWith("repository.java")
                || dirPath.contains("/mapper/") || dirPath.contains("/dao/") || dirPath.contains("/repository/")) return DiffFileRoleEnum.DAO;
        if (lower.endsWith(".sql")) return DiffFileRoleEnum.SQL;
        if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) return DiffFileRoleEnum.CONFIG;
        if (fileName.endsWith("job.java") || fileName.endsWith("scheduler.java") || fileName.endsWith("task.java")
                || dirPath.contains("/job/") || dirPath.contains("/scheduler/")) return DiffFileRoleEnum.JOB;
        if (fileName.contains("mq") || fileName.contains("consumer") || fileName.contains("producer")
                || dirPath.contains("/mq/") || dirPath.contains("/message/")) return DiffFileRoleEnum.MQ;
        return DiffFileRoleEnum.OTHER;
    }

    public boolean isTestFile(String path) {
        return classify(path) == DiffFileRoleEnum.TEST;
    }

    private boolean isTestLikeFileName(String fileName) {
        boolean testExtension = fileName.endsWith(".java") || fileName.endsWith(".ts") || fileName.endsWith(".tsx")
                || fileName.endsWith(".js") || fileName.endsWith(".jsx");
        return testExtension && (fileName.contains("test") || fileName.contains(".spec."));
    }
}
