package com.example.aitestops.diff.git;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.diff.enums.DiffChangeTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GitDiffClient {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_PATCH_LENGTH = 30_000;

    public GitDiffResult diff(String repoUrl, String sourceBranch, String targetBranch) {
        validateGitInput(repoUrl, sourceBranch, targetBranch);
        try {
            Path repoDir = prepareRepository(repoUrl);
            runGit(repoDir, "fetch", "--all", "--prune");
            String sourceRef = resolveRef(repoDir, sourceBranch);
            String targetRef = resolveRef(repoDir, targetBranch);
            String baseCommit = runGit(repoDir, "rev-parse", targetRef).trim();
            String headCommit = runGit(repoDir, "rev-parse", sourceRef).trim();
            String range = targetRef + "..." + sourceRef;
            List<NameStatus> nameStatuses = parseNameStatus(runGit(repoDir, "diff", "--name-status", "--find-renames", range));
            Map<String, NumStat> stats = parseNumStat(runGit(repoDir, "diff", "--numstat", "--find-renames", range));
            List<GitChangedFile> files = new ArrayList<>();
            for (NameStatus item : nameStatuses) {
                String pathForPatch = DiffChangeTypeEnum.DELETED.name().equals(item.changeType()) ? item.oldPath() : item.newPath();
                NumStat stat = stats.getOrDefault(statKey(item), new NumStat(0, 0));
                String patch = readPatch(repoDir, range, pathForPatch);
                files.add(new GitChangedFile(item.oldPath(), item.newPath(), item.changeType(), detectLanguage(item.newPath()),
                        stat.additions(), stat.deletions(), truncate(patch, MAX_PATCH_LENGTH)));
            }
            return new GitDiffResult(resolveRepoName(repoUrl), baseCommit, headCommit, files);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Git Diff 获取失败: repoUrl={}, sourceBranch={}, targetBranch={}", repoUrl, sourceBranch, targetBranch, ex);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git Diff 获取失败: " + ex.getMessage(), ex);
        }
    }

    private void validateGitInput(String repoUrl, String sourceBranch, String targetBranch) {
        if (!StringUtils.hasText(repoUrl)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址不能为空");
        }
        if (!StringUtils.hasText(sourceBranch)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能为空");
        }
        if (!StringUtils.hasText(targetBranch)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标分支不能为空");
        }
        if (sourceBranch.equals(targetBranch)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能和目标分支相同");
        }
        if (repoUrl.length() > 500 || sourceBranch.length() > 255 || targetBranch.length() > 255) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址或分支名称过长");
        }
    }

    private Path prepareRepository(String repoUrl) throws IOException, InterruptedException {
        Path cacheRoot = Path.of("data", "git-cache").toAbsolutePath().normalize();
        Files.createDirectories(cacheRoot);
        String hash = DigestUtils.md5DigestAsHex(repoUrl.getBytes(StandardCharsets.UTF_8));
        Path repoDir = cacheRoot.resolve(hash);
        if (Files.exists(repoDir.resolve(".git"))) {
            return repoDir;
        }
        if (Files.exists(repoDir)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 缓存目录已存在但不是有效仓库: " + repoDir);
        }
        runCommand(cacheRoot, List.of("git", "clone", "--no-tags", repoUrl, repoDir.toString()));
        return repoDir;
    }

    private String resolveRef(Path repoDir, String branch) throws IOException, InterruptedException {
        List<String> candidates = List.of("origin/" + branch, branch);
        for (String candidate : candidates) {
            try {
                runGit(repoDir, "rev-parse", "--verify", candidate);
                return candidate;
            } catch (BusinessException ignored) {
                // try next candidate
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "分支不存在: " + branch);
    }

    private String runGit(Path repoDir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoDir.toString());
        command.addAll(List.of(args));
        return runCommand(repoDir, command);
    }

    private String runCommand(Path workingDir, List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 命令执行超时");
        }
        if (process.exitValue() != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, output.isBlank() ? "Git 命令执行失败" : output.trim());
        }
        return output;
    }

    private List<NameStatus> parseNameStatus(String output) {
        List<NameStatus> items = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length < 2) {
                continue;
            }
            String status = parts[0];
            if (status.startsWith("R") && parts.length >= 3) {
                items.add(new NameStatus(parts[1], parts[2], DiffChangeTypeEnum.RENAMED.name()));
            } else if (status.startsWith("A")) {
                items.add(new NameStatus(null, parts[1], DiffChangeTypeEnum.ADDED.name()));
            } else if (status.startsWith("D")) {
                items.add(new NameStatus(parts[1], parts[1], DiffChangeTypeEnum.DELETED.name()));
            } else {
                items.add(new NameStatus(null, parts[1], DiffChangeTypeEnum.MODIFIED.name()));
            }
        }
        return items;
    }

    private Map<String, NumStat> parseNumStat(String output) {
        Map<String, NumStat> stats = new HashMap<>();
        for (String line : output.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length < 3) {
                continue;
            }
            int additions = parseStatNumber(parts[0]);
            int deletions = parseStatNumber(parts[1]);
            String key = parts.length >= 4 ? parts[3] : parts[2];
            stats.put(key, new NumStat(additions, deletions));
        }
        return stats;
    }

    private int parseStatNumber(String value) {
        if (!StringUtils.hasText(value) || "-".equals(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private String statKey(NameStatus item) {
        return DiffChangeTypeEnum.DELETED.name().equals(item.changeType()) ? item.oldPath() : item.newPath();
    }

    private String readPatch(Path repoDir, String range, String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        try {
            return runGit(repoDir, "diff", "--find-renames", range, "--", path);
        } catch (Exception ex) {
            log.debug("文件 Patch 获取失败: path={}", path, ex);
            return "";
        }
    }

    private String detectLanguage(String path) {
        if (!StringUtils.hasText(path)) {
            return "OTHER";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "JAVA";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TYPESCRIPT";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "JAVASCRIPT";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "YAML";
        if (lower.endsWith(".properties")) return "PROPERTIES";
        if (lower.endsWith(".md")) return "MARKDOWN";
        return "OTHER";
    }

    private String resolveRepoName(String repoUrl) {
        String normalized = repoUrl.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record NameStatus(String oldPath, String newPath, String changeType) {
    }

    private record NumStat(int additions, int deletions) {
    }
}
