# Prompt 版本化与质量闭环

第四阶段目标是让 Prompt 调优可追溯、可回滚、可对比，不再直接覆盖同一个模板内容。

## 1. 数据模型

`ai_testops_prompt_template` 支持同一个 `template_code` 下多版本并存：

```text
template_code + version 唯一
同一个 template_code 只启用一个版本
generation_record 记录 prompt_template_code 和 prompt_template_version
```

质量闭环使用已有数据聚合：

```text
generation_record：模型、Prompt 版本、输入快照、输出、状态、token
validation_result：JSON/必填字段/格式/重复校验结果
test_case_draft：生成草稿数量和评审状态
review_record：人工编辑、确认、驳回动作
```

## 2. API

查询 Prompt 版本：

```text
GET /api/ai-testops/prompts?templateCode=TEST_CASE_GENERATE
```

创建新版本：

```text
POST /api/ai-testops/prompts
```

```json
{
  "templateCode": "TEST_CASE_GENERATE",
  "templateName": "测试用例生成模板",
  "templateType": "TEST_CASE_GENERATE",
  "version": "v1.1.0",
  "promptContent": "只能输出 JSON ...",
  "jsonSchema": "{\"type\":\"object\",\"required\":[\"test_cases\"]}",
  "enabled": false
}
```

启用版本：

```text
POST /api/ai-testops/prompts/TEST_CASE_GENERATE/versions/v1.1.0/activate
```

查询质量指标：

```text
GET /api/ai-testops/prompts/quality?templateCode=TEST_CASE_GENERATE
```

## 3. 质量指标

质量接口按 Prompt 版本返回：

```text
totalGenerations
successGenerations
failedGenerations
validationFailedGenerations
draftCount
approvedCount
rejectedCount
editedCount
successRate
validationPassRate
approveRate
avgTokenInput
avgTokenOutput
```

建议调优时每次只改一个变量，例如：

```text
v1.1.0：增强 JSON-only 约束
v1.2.0：增强异常/边界/权限场景
v1.3.0：增强 requirement_refs 约束
v1.4.0：增加 coverage_summary
```

对比同一份需求文档在不同 Prompt 版本下的 `validationPassRate`、`approveRate` 和人工 `editedCount`，再决定是否启用新版本。
