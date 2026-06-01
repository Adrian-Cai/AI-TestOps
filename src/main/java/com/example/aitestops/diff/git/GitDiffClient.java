package com.example.aitestops.diff.git;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.diff.enums.DiffChangeTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Git Diff 客户端。
 * <p>
 * 负责与 Git 仓库交互，获取分支列表和代码变更差异，
 * 支持本地仓库和远程仓库（GitHub 等）。
 * </p>
 */
@Slf4j
@Component
public class GitDiffClient {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration OUTPUT_DRAIN_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_PATCH_LENGTH = 30_000;

    @Value("${ai-testops.diff.git.allow-local-repository:false}")
    private boolean allowLocalRepository = false;

    @Value("${ai-testops.diff.git.allowed-hosts:github.com}")
    private String allowedHosts = "github.com";

    private final Map<String, List<String>> branchCache = new ConcurrentHashMap<>();

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
            log.warn("Git Diff failed: repoUrl={}, sourceBranch={}, targetBranch={}", repoUrl, sourceBranch, targetBranch, ex);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git Diff 获取失败: " + ex.getMessage(), ex);
        }
    }

    public List<String> listBranches(String repoUrl) {
        validateRepositoryUrl(repoUrl);
        if (isLocalPath(repoUrl)) {
            try {
                Path repoDir = Path.of(repoUrl).toAbsolutePath().normalize();
                if (!Files.exists(repoDir.resolve(".git"))) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "本地仓库路径不是有效 Git 仓库");
                }
                return cacheBranches(repoUrl, parseLocalBranches(runGit(repoDir, "branch", "--format=%(refname:short)")));
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Git local branch list failed: repoUrl={}", repoUrl, ex);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 本地分支列表获取失败: " + ex.getMessage(), ex);
            }
        }

        try {
            List<String> branches = parseRemoteBranches(runCommand(Path.of(".").toAbsolutePath().normalize(),
                    List.of("git", "ls-remote", "--heads", repoUrl)));
            return cacheBranches(repoUrl, branches);
        } catch (Exception ex) {
            List<String> fallbackBranches = fallbackRemoteBranches(repoUrl);
            if (!fallbackBranches.isEmpty()) {
                log.warn("Git remote branch list failed, using cached branches: repoUrl={}, message={}", repoUrl, ex.getMessage());
                return fallbackBranches;
            }
            log.warn("Git remote branch list failed: repoUrl={}, message={}", repoUrl, ex.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, normalizeRemoteGitError(ex.getMessage()), ex);
        }
    }

    private void validateGitInput(String repoUrl, String sourceBranch, String targetBranch) {
        validateRepositoryUrl(repoUrl);
        if (!StringUtils.hasText(sourceBranch)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能为空");
        }
        if (!StringUtils.hasText(targetBranch)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标分支不能为空");
        }
        if (sourceBranch.equals(targetBranch)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能和目标分支相同");
        }
        if (sourceBranch.length() > 255 || targetBranch.length() > 255) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分支名称过长");
        }
    }

    private void validateRepositoryUrl(String repoUrl) {
        if (!StringUtils.hasText(repoUrl)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址不能为空");
        }
        if (repoUrl.startsWith("-")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址格式不合法");
        }
        if (isLocalPath(repoUrl)) {
            if (!allowLocalRepository) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "本地仓库路径未启用");
            }
        } else {
            validateRemoteRepositoryUrl(repoUrl);
        }
        if (repoUrl.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址过长");
        }
    }

    private boolean isLocalPath(String repoUrl) {
        return repoUrl.startsWith("/") || repoUrl.matches("[A-Za-z]:\\\\.*") || repoUrl.matches("[A-Za-z]:/.*");
    }

    private void validateRemoteRepositoryUrl(String repoUrl) {
        URI uri;
        try {
            uri = URI.create(repoUrl);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址格式不合法", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址仅支持 HTTPS 远程仓库");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Set<String> allowList = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (allowList.isEmpty() || !allowList.contains(host)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址不在允许的 Git 主机白名单内: " + host);
        }
    }

    private Path prepareRepository(String repoUrl) throws IOException, InterruptedException {
        Path cacheRoot = gitCacheRoot();
        Files.createDirectories(cacheRoot);
        Path repoDir = gitCacheDir(repoUrl);
        if (Files.exists(repoDir.resolve(".git"))) {
            return repoDir;
        }
        if (Files.exists(repoDir)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 缓存目录已存在但不是有效仓库: " + repoDir);
        }
        runCommand(cacheRoot, List.of("git", "clone", "--no-tags", repoUrl, repoDir.toString()));
        return repoDir;
    }

    private Path gitCacheRoot() {
        return Path.of("data", "git-cache").toAbsolutePath().normalize();
    }

    private Path gitCacheDir(String repoUrl) {
        String hash = DigestUtils.md5DigestAsHex(Objects.requireNonNull(repoUrl.getBytes(StandardCharsets.UTF_8)));
        return gitCacheRoot().resolve(hash);
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
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 命令执行超时");
        }
        String output = awaitOutput(outputFuture);
        if (process.exitValue() != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, output.isBlank() ? "Git 命令执行失败" : output.trim());
        }
        return output;
    }

    private String readOutput(InputStream inputStream) {
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CompletionException(ex);
        }
    }

    private String awaitOutput(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(OUTPUT_DRAIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取 Git 命令输出被中断", ex);
        } catch (ExecutionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取 Git 命令输出失败", ex);
        } catch (TimeoutException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取 Git 命令输出超时", ex);
        }
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

    private List<String> parseRemoteBranches(String output) {
        return output.lines()
                .map(line -> {
                    int index = line.indexOf("refs/heads/");
                    return index >= 0 ? line.substring(index + "refs/heads/".length()).trim() : "";
                })
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> fallbackRemoteBranches(String repoUrl) {
        List<String> cachedBranches = branchCache.get(repoUrl);
        if (cachedBranches != null && !cachedBranches.isEmpty()) {
            return cachedBranches;
        }
        Path repoDir = gitCacheDir(repoUrl);
        if (!Files.exists(repoDir.resolve(".git"))) {
            return List.of();
        }
        try {
            List<String> branches = parseCachedRemoteBranches(runGit(repoDir,
                    "for-each-ref", "--format=%(refname:short)", "refs/remotes/origin", "refs/heads"));
            return cacheBranches(repoUrl, branches);
        } catch (Exception ex) {
            log.debug("Git cached branch fallback failed: repoUrl={}", repoUrl, ex);
            return List.of();
        }
    }

    private List<String> parseCachedRemoteBranches(String output) {
        return output.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(item -> !"HEAD".equals(item) && !"origin/HEAD".equals(item))
                .map(item -> item.startsWith("origin/") ? item.substring("origin/".length()) : item)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> parseLocalBranches(String output) {
        return output.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> cacheBranches(String repoUrl, List<String> branches) {
        List<String> safeBranches = branches == null ? List.of() : List.copyOf(branches);
        if (!safeBranches.isEmpty()) {
            branchCache.put(repoUrl, safeBranches);
        }
        return safeBranches;
    }

    private String normalizeRemoteGitError(String message) {
        String text = message == null ? "" : message;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("could not resolve host") || lower.contains("failed to connect")
                || lower.contains("connection timed out") || lower.contains("network is unreachable")) {
            return "无法连接 Git 远程仓库，请检查服务器网络、DNS 或代理配置；也可以稍后重试。";
        }
        return StringUtils.hasText(text) ? text.trim() : "Git 远程分支列表获取失败";
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
            log.debug("File patch read failed: path={}", path, ex);
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

    public static String resolveRepoName(String repoUrl) {
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
