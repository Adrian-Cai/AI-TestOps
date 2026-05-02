package com.example.aitestops.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程 MariaDB 表结构一次性迁移工具。
 *
 * <p>该类只在手动执行 Maven exec 命令时运行，不参与应用启动和自动化测试。
 * DDL 来源于 docs/sql/ai_testops_mariadb_schema.sql，原则上只创建缺失表、
 * 补齐缺失字段和索引，不执行 DROP/TRUNCATE。</p>
 */
public final class RemoteMariaDbSchemaMigrator {

    private static final Path ENV_PATH = Path.of(".env");
    private static final Path DDL_PATH = Path.of("docs/sql/ai_testops_mariadb_schema.sql");
    private static final String TABLE_PREFIX = "ai_testops_%";
    private static final Map<String, Map<String, String>> LEGACY_NULLABLE_COLUMNS = legacyNullableColumns();

    private RemoteMariaDbSchemaMigrator() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> env = loadEnv();
        String url = resolveUrl(env);
        String username = firstPresent(env, "DB_USERNAME", "DB_USER");
        String password = env.getOrDefault("DB_PASSWORD", "");

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(".env 缺少 DB_USERNAME 或 DB_USER");
        }

        Class.forName("org.mariadb.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            System.out.println("Connected to MariaDB database=" + currentDatabase(connection));
            printSchemaSummary(connection, "before");
            int executed = executeDdl(connection);
            int compatibilityExecuted = executeLegacyCompatibilityDdl(connection);
            System.out.println("DDL executed count=" + executed + ", compatibility count=" + compatibilityExecuted);
            printSchemaSummary(connection, "after");
        }
    }

    private static Map<String, String> loadEnv() throws IOException {
        if (!Files.exists(ENV_PATH)) {
            throw new IllegalStateException(".env 文件不存在，无法读取远程数据库配置");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(ENV_PATH, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            String key = parts[0].trim();
            String value = stripQuotes(parts[1].trim());
            values.put(key, value);
        }
        return values;
    }

    private static String resolveUrl(Map<String, String> env) {
        String url = env.get("DB_URL");
        if (url != null && !url.isBlank()) {
            return url;
        }
        String host = env.getOrDefault("DB_HOST", "localhost");
        String port = env.getOrDefault("DB_PORT", "3306");
        String database = env.getOrDefault("DB_NAME", "ai_testops");
        return "jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    }

    private static String firstPresent(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String value = env.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int executeDdl(Connection connection) throws IOException, SQLException {
        String ddl = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        List<String> statements = splitStatements(ddl);
        int executed = 0;
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                statement.execute(trimmed);
                executed++;
            }
        } catch (SQLException ex) {
            throw new SQLException("DDL 执行失败，statement=" + abbreviate(extractFailedStatement(statements, executed)), ex);
        }
        return executed;
    }

    private static int executeLegacyCompatibilityDdl(Connection connection) throws SQLException {
        int executed = 0;
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, Map<String, String>> tableEntry : LEGACY_NULLABLE_COLUMNS.entrySet()) {
                String tableName = tableEntry.getKey();
                if (!tableExists(connection, tableName)) {
                    continue;
                }
                for (Map.Entry<String, String> columnEntry : tableEntry.getValue().entrySet()) {
                    if (!columnExists(connection, tableName, columnEntry.getKey())) {
                        continue;
                    }
                    statement.execute("ALTER TABLE " + tableName + " MODIFY COLUMN "
                            + columnEntry.getKey() + " " + columnEntry.getValue() + " NULL");
                    executed++;
                }
            }
        }
        return executed;
    }

    private static List<String> splitStatements(String ddl) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < ddl.length(); i++) {
            char ch = ddl.charAt(i);
            if (ch == ';') {
                statements.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static String extractFailedStatement(List<String> statements, int executed) {
        if (executed >= 0 && executed < statements.size()) {
            return statements.get(executed);
        }
        return "unknown";
    }

    private static String abbreviate(String value) {
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 240 ? oneLine : oneLine.substring(0, 240) + "...";
    }

    private static String currentDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '"
                + tableName + "'";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '"
                + tableName + "' AND column_name = '" + columnName + "'";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }

    private static void printSchemaSummary(Connection connection, String stage) throws SQLException {
        Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
        String sql = """
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name LIKE '%s'
                ORDER BY table_name, ordinal_position
                """.formatted(TABLE_PREFIX);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                columnsByTable.computeIfAbsent(resultSet.getString("table_name"), key -> new ArrayList<>())
                        .add(resultSet.getString("column_name"));
            }
        }
        System.out.println("Schema " + stage + " tableCount=" + columnsByTable.size());
        columnsByTable.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> System.out.println("  " + entry.getKey() + " columns=" + entry.getValue().size()
                        + " " + String.join(",", entry.getValue())));
    }

    private static Map<String, Map<String, String>> legacyNullableColumns() {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        tables.put("ai_testops_document", orderedMap(
                "original_file_name", "VARCHAR(255)",
                "status", "VARCHAR(32)",
                "created_by", "VARCHAR(128)"
        ));
        tables.put("ai_testops_document_chunk", orderedMap(
                "char_count", "INT",
                "embedding_status", "VARCHAR(32)",
                "vector_ref", "VARCHAR(255)"
        ));
        tables.put("ai_testops_document_parse_result", orderedMap(
                "raw_text", "LONGTEXT",
                "clean_text", "LONGTEXT",
                "parse_status", "VARCHAR(32)",
                "parse_error", "VARCHAR(1000)",
                "parser_name", "VARCHAR(64)",
                "parser_version", "VARCHAR(32)"
        ));
        tables.put("ai_testops_prompt_template", orderedMap(
                "prompt_code", "VARCHAR(64)",
                "prompt_name", "VARCHAR(128)",
                "prompt_version", "VARCHAR(32)",
                "prompt_type", "VARCHAR(64)",
                "system_prompt", "LONGTEXT",
                "user_prompt_template", "LONGTEXT",
                "output_schema", "LONGTEXT",
                "created_by", "VARCHAR(128)"
        ));
        tables.put("ai_testops_generation_record", orderedMap(
                "prompt_code", "VARCHAR(64)",
                "prompt_version", "VARCHAR(32)",
                "model_provider", "VARCHAR(64)",
                "input_snapshot", "LONGTEXT",
                "raw_response", "LONGTEXT",
                "validation_status", "VARCHAR(32)",
                "validation_result", "LONGTEXT",
                "cost_amount", "DECIMAL(18,6)",
                "duration_ms", "BIGINT"
        ));
        tables.put("ai_testops_requirement_extract", orderedMap(
                "extract_status", "VARCHAR(32)"
        ));
        tables.put("ai_testops_validation_result", orderedMap(
                "document_id", "VARCHAR(64)",
                "target_type", "VARCHAR(64)",
                "target_id", "VARCHAR(64)",
                "validation_stage", "VARCHAR(64)",
                "schema_valid", "TINYINT",
                "required_fields_valid", "TINYINT",
                "duplicate_valid", "TINYINT",
                "format_valid", "TINYINT",
                "overall_status", "VARCHAR(32)",
                "error_count", "INT",
                "warning_count", "INT",
                "validation_rules", "LONGTEXT",
                "validation_detail", "LONGTEXT",
                "error_message", "VARCHAR(1000)"
        ));
        tables.put("ai_testops_test_case_draft", orderedMap(
                "preconditions", "LONGTEXT",
                "steps", "LONGTEXT",
                "expected_result", "LONGTEXT",
                "requirement_refs", "LONGTEXT",
                "risk_tags", "LONGTEXT",
                "ai_generated", "TINYINT",
                "review_comment", "VARCHAR(1000)"
        ));
        tables.put("ai_testops_test_case", orderedMap(
                "draft_case_id", "VARCHAR(64)",
                "preconditions", "LONGTEXT",
                "steps", "LONGTEXT",
                "expected_result", "LONGTEXT",
                "requirement_refs", "LONGTEXT",
                "risk_tags", "LONGTEXT",
                "confirmed_by", "VARCHAR(128)",
                "confirmed_at", "DATETIME"
        ));
        tables.put("ai_testops_requirement_case_mapping", orderedMap(
                "requirement_text", "LONGTEXT",
                "case_id", "VARCHAR(64)",
                "mapping_type", "VARCHAR(64)",
                "confidence", "DECIMAL(10,4)"
        ));
        tables.put("ai_testops_review_record", orderedMap(
                "review_id", "VARCHAR(64)",
                "document_id", "VARCHAR(64)",
                "generation_id", "VARCHAR(64)",
                "review_object_type", "VARCHAR(64)",
                "review_action", "VARCHAR(64)",
                "review_status_before", "VARCHAR(32)",
                "review_status_after", "VARCHAR(32)",
                "original_content", "LONGTEXT",
                "modified_content", "LONGTEXT",
                "change_summary", "LONGTEXT",
                "review_comment", "VARCHAR(1000)",
                "reviewed_at", "DATETIME"
        ));
        return tables;
    }

    private static Map<String, String> orderedMap(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
