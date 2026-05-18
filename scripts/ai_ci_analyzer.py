#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""Generate a Jenkins CI risk report from diff, JUnit and JaCoCo outputs."""

from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
import textwrap
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


LOG_FORMAT = "%(asctime)s [%(levelname)s] %(message)s"
DEFAULT_MAX_DIFF_CHARS = 60000
LOW_CLASS_COVERAGE = 0.60
SECRET_PATTERN = re.compile(r"(sk|ak)-[A-Za-z0-9_-]{8,}")

logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger("ai_ci_analyzer")


def read_text_file(file_path: str, max_chars: int = DEFAULT_MAX_DIFF_CHARS) -> str:
    path = Path(file_path)
    if not path.exists():
        logger.warning("File does not exist: %s", file_path)
        return ""

    content = path.read_text(encoding="utf-8", errors="ignore")
    if len(content) <= max_chars:
        return content

    logger.warning("File is too large and has been truncated: %s", file_path)
    return content[:max_chars] + "\n\n[content truncated]"


def parse_junit_reports(report_dir: str) -> dict[str, Any]:
    result: dict[str, Any] = {
        "total": 0,
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "failed_cases": [],
    }

    report_path = Path(report_dir)
    if not report_path.exists():
        logger.warning("JUnit report directory does not exist: %s", report_dir)
        return result

    for xml_file in sorted(report_path.glob("*.xml")):
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as exc:
            logger.warning("Failed to parse JUnit report %s: %s", xml_file, exc)
            continue

        testcases = root.findall(".//testcase")
        if testcases:
            result["total"] += len(testcases)
        else:
            result["total"] += int(root.attrib.get("tests", 0) or 0)

        result["skipped"] += int(root.attrib.get("skipped", 0) or 0)

        for case in testcases:
            skipped = case.find("skipped")
            failure = case.find("failure")
            error = case.find("error")

            if skipped is not None and not root.attrib.get("skipped"):
                result["skipped"] += 1

            if failure is None and error is None:
                continue

            node = failure if failure is not None else error
            if failure is not None:
                result["failures"] += 1
            else:
                result["errors"] += 1

            result["failed_cases"].append(
                {
                    "class_name": case.attrib.get("classname", ""),
                    "test_name": case.attrib.get("name", ""),
                    "message": (node.attrib.get("message", "") or "").strip()[:500],
                }
            )

    return result


def _coverage_ratio(counter: ET.Element) -> float | None:
    missed = int(counter.attrib.get("missed", 0) or 0)
    covered = int(counter.attrib.get("covered", 0) or 0)
    total = missed + covered
    if total == 0:
        return None
    return round(covered / total, 4)


def parse_jacoco_xml(jacoco_xml: str) -> dict[str, Any]:
    result: dict[str, Any] = {
        "line_coverage": None,
        "branch_coverage": None,
        "method_coverage": None,
        "instruction_coverage": None,
        "low_coverage_classes": [],
    }

    path = Path(jacoco_xml)
    if not path.exists():
        logger.warning("JaCoCo XML does not exist: %s", jacoco_xml)
        return result

    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        logger.warning("Failed to parse JaCoCo XML %s: %s", jacoco_xml, exc)
        return result

    field_map = {
        "LINE": "line_coverage",
        "BRANCH": "branch_coverage",
        "METHOD": "method_coverage",
        "INSTRUCTION": "instruction_coverage",
    }
    for counter in root.findall("counter"):
        field_name = field_map.get(counter.attrib.get("type"))
        if field_name:
            result[field_name] = _coverage_ratio(counter)

    for package in root.findall("package"):
        package_name = package.attrib.get("name", "")
        for clazz in package.findall("class"):
            class_name = clazz.attrib.get("name", "")
            line_counter = next(
                (counter for counter in clazz.findall("counter") if counter.attrib.get("type") == "LINE"),
                None,
            )
            if line_counter is None:
                continue

            ratio = _coverage_ratio(line_counter)
            if ratio is not None and ratio < LOW_CLASS_COVERAGE:
                result["low_coverage_classes"].append(
                    {
                        "class_name": f"{package_name}/{class_name}".strip("/"),
                        "line_coverage": ratio,
                    }
                )

    result["low_coverage_classes"].sort(key=lambda item: item["line_coverage"])
    result["low_coverage_classes"] = result["low_coverage_classes"][:20]
    return result


def extract_changed_files(diff_stat: str, diff_content: str) -> list[str]:
    files: list[str] = []
    seen: set[str] = set()

    for line in diff_content.splitlines():
        if line.startswith("diff --git "):
            match = re.match(r"diff --git a/(.*?) b/(.*)$", line)
            if match:
                candidate = match.group(2)
                if candidate not in seen:
                    seen.add(candidate)
                    files.append(candidate)

    if files:
        return files

    for line in diff_stat.splitlines():
        if "|" not in line:
            continue
        candidate = line.split("|", 1)[0].strip()
        if candidate and candidate not in seen:
            seen.add(candidate)
            files.append(candidate)

    return files


def percent(value: float | None) -> str:
    if value is None:
        return "N/A"
    return f"{value * 100:.2f}%"


def redact_sensitive(text: str) -> str:
    redacted = SECRET_PATTERN.sub(r"\1-[REDACTED]", text)
    for env_name in ("AI_API_KEY", "OPENAI_API_KEY"):
        secret = os.getenv(env_name)
        if secret and len(secret) >= 8:
            redacted = redacted.replace(secret, "[REDACTED]")
    return redacted


def build_prompt(
    project: str,
    diff_stat: str,
    diff_content: str,
    junit_summary: dict[str, Any],
    coverage_summary: dict[str, Any],
    changed_files: list[str],
) -> str:
    return f"""
你是一名资深测试架构师、质量保障负责人和 CI/CD 质量门禁设计专家。

请基于 Jenkins CI/CD 采集到的代码 Diff、变更文件、JUnit 单测结果、JaCoCo 覆盖率结果，生成一份“上线前风险分析报告”。

报告目标：
1. 判断本次变更是否可以上线；
2. 识别影响模块、接口、业务流；
3. 给出测试回归重点；
4. 给出线上 Bug 优先排查方向；
5. 输出可执行的上线前检查清单。

重要约束：
- 必须严格基于输入信息分析。
- 不要编造不存在的接口、类、方法、业务流。
- 如果只能推测，必须标注“推测”。
- 如果信息不足，必须说明“不足项”和“判断偏差”。
- 不要输出密钥、Token、密码、环境变量值、数据库连接串等敏感信息。

项目名称：
{project}

一、本次代码 Diff 统计：
{diff_stat or "未采集到 diff 统计"}

二、本次变更文件：
{json.dumps(changed_files, ensure_ascii=False, indent=2)}

三、本次代码 Diff 内容：
{diff_content or "未采集到 diff 内容"}

四、JUnit 单测结果：
{json.dumps(junit_summary, ensure_ascii=False, indent=2)}

五、JaCoCo 覆盖率结果：
{json.dumps(coverage_summary, ensure_ascii=False, indent=2)}

风险等级判断规则：
- 低风险：单测通过，覆盖率无明显问题，变更范围小，未涉及核心链路。
- 中风险：单测通过但覆盖率偏低，或涉及 Service、Controller、Mapper、配置、定时任务、接口参数、状态流转、数据读写等关键逻辑。
- 高风险：存在失败单测，或核心链路覆盖不足，或涉及支付、订单、库存、结算、权限、登录、事务、缓存、消息、金额计算、状态机等高风险变更。

上线建议规则：
- 建议上线：低风险，且单测通过，覆盖率无明显问题。
- 谨慎上线：中风险，需要人工复核或补充回归。
- 不建议上线：高风险，或存在失败单测，或核心变更缺少测试覆盖。

请输出 Markdown，结构如下：

# AI 上线风险分析报告

## 1. 结论摘要

| 项目 | 结论 |
|---|---|
| 风险等级 | 低 / 中 / 高 |
| 上线建议 | 建议上线 / 谨慎上线 / 不建议上线 |
| 核心风险 | 1-3 条 |
| 是否存在失败单测 | 是 / 否 / 未采集到 |
| 覆盖率是否充分 | 是 / 否 / 无法判断 |
| 是否需要人工复核 | 是 / 否 |

 请补充 3-5 句话说明结论依据。

## 2. 本次变更影响范围

| 变更文件/模块 | 变更类型 | 可能影响范围 | 风险说明 | 证据来源 |
|---|---|---|---|---|

变更类型包括：Controller、Service、Mapper、Config、Job、DTO、Util、Test、Unknown。

## 3. 单测结果分析

输出单测总数、通过数量、失败数量、错误数量、跳过数量。
如果有失败用例，输出失败用例、所属类、失败原因、影响模块、建议处理。
如果没有失败用例，也要说明是否仍存在覆盖不足风险。

## 4. 覆盖率分析

| 指标 | 当前值 | 判断 |
|---|---|---|
| 行覆盖率 | xx% / 未采集到 | 充分 / 偏低 / 无法判断 |
| 分支覆盖率 | xx% / 未采集到 | 充分 / 偏低 / 无法判断 |
| 方法覆盖率 | xx% / 未采集到 | 充分 / 偏低 / 无法判断 |

输出低覆盖率类、风险说明和建议补充测试。
如果没有增量覆盖率数据，必须说明无法确认本次新增/修改代码是否被覆盖。

## 5. 接口覆盖率与测试补充建议

| 接口/推测接口 | 关联变更 | 当前风险 | 正常场景 | 异常场景 | 边界场景 |
|---|---|---|---|---|---|

无法识别接口时，请写“未能从当前输入中识别明确接口”，并给出模块级测试建议。

## 6. 业务流风险预判

| 业务流/推测业务流 | 关联模块 | 风险点 | 可能线上表现 | 建议回归场景 |
|---|---|---|---|---|

重点关注：状态流转、数据一致性、兼容性、幂等、并发、异常重试、权限、金额计算、缓存一致性。

## 7. 线上 Bug 排查建议

### 7.1 优先排查对象

| 优先级 | 类/方法/接口/模块 | 排查原因 | 建议查看的日志关键词 |
|---|---|---|---|

### 7.2 可能的问题类型

只输出与本次变更相关的问题类型。

## 8. 上线前检查清单

输出可执行 checklist，必须结合本次变更，不要只输出通用项。

## 9. 信息不足与改进建议

| 缺失信息 | 对风险判断的影响 | 建议补充 |
|---|---|---|

常见缺失信息包括：需求文档、接口清单、业务流映射、增量覆盖率、接口自动化结果、线上日志规范、监控告警配置、数据库变更 SQL、配置变更说明。
""".strip()


def chat_completions_url(api_base: str) -> str:
    base = (api_base or "https://api.openai.com").rstrip("/")
    if base.endswith("/v1/chat/completions"):
        return base
    if base.endswith("/v1"):
        return f"{base}/chat/completions"
    return f"{base}/v1/chat/completions"


def call_ai(prompt: str) -> str:
    provider = os.getenv("AI_PROVIDER", "MOCK").strip()
    api_key = os.getenv("AI_API_KEY") or os.getenv("OPENAI_API_KEY")
    if provider.upper() == "MOCK":
        raise RuntimeError("AI_PROVIDER=MOCK, skip remote AI call")
    if not api_key:
        raise RuntimeError("AI_API_KEY or OPENAI_API_KEY is not configured")

    model_name = os.getenv("AI_MODEL_NAME", "gpt-4o-mini")
    temperature = float(os.getenv("AI_TEMPERATURE", "0.2"))
    max_tokens = int(os.getenv("AI_MAX_TOKENS", "4096"))
    endpoint = chat_completions_url(os.getenv("AI_API_BASE", "https://api.openai.com"))

    body = {
        "model": model_name,
        "messages": [
            {
                "role": "system",
                "content": "你是严格、务实的 CI/CD 质量风险分析助手。",
            },
            {"role": "user", "content": prompt},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            raw_response = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        detail = redact_sensitive(exc.read().decode("utf-8", errors="replace")[:1000])
        raise RuntimeError(f"AI HTTP {exc.code}: {detail}") from exc

    data = json.loads(raw_response)
    content = data.get("choices", [{}])[0].get("message", {}).get("content")
    if not content:
        raise RuntimeError("AI response choices[0].message.content is empty")
    return content


def risk_level(junit_summary: dict[str, Any], coverage_summary: dict[str, Any], changed_files: list[str]) -> str:
    if junit_summary["failures"] or junit_summary["errors"]:
        return "高"
    line_coverage = coverage_summary.get("line_coverage")
    branch_coverage = coverage_summary.get("branch_coverage")
    if line_coverage is not None and line_coverage < 0.50:
        return "高"
    if branch_coverage is not None and branch_coverage < 0.30:
        return "中"
    if len(changed_files) >= 20:
        return "中"
    return "低"


def local_risk_report(
    project: str,
    diff_stat: str,
    junit_summary: dict[str, Any],
    coverage_summary: dict[str, Any],
    changed_files: list[str],
    reason: str,
) -> str:
    level = risk_level(junit_summary, coverage_summary, changed_files)
    advice = "不建议上线" if level == "高" else ("谨慎上线" if level == "中" else "建议上线")
    failed_cases = junit_summary.get("failed_cases", [])
    low_classes = coverage_summary.get("low_coverage_classes", [])
    changed_file_lines = "\n".join(f"- {item}" for item in changed_files[:30]) or "- 未采集到变更文件"
    failed_case_lines = (
        "\n".join(
            f"- {case.get('class_name')}#{case.get('test_name')}: {case.get('message') or '无失败消息'}"
            for case in failed_cases[:20]
        )
        or "- 无失败用例"
    )
    low_class_lines = (
        "\n".join(f"- {item['class_name']}: {percent(item['line_coverage'])}" for item in low_classes[:20])
        or "- 未发现低于 60% 行覆盖率的类，或未采集到类级覆盖率"
    )

    return textwrap.dedent(
        f"""
        # AI 上线风险分析报告

        > 远程 AI 未执行或调用失败，当前报告由本地规则生成。原因：{reason}

        ## 1. 结论摘要
        - 项目：{project}
        - 本次变更风险等级：{level}
        - 是否建议上线：{advice}
        - 核心原因：单测失败数 {junit_summary['failures']}，错误数 {junit_summary['errors']}，行覆盖率 {percent(coverage_summary.get('line_coverage'))}，分支覆盖率 {percent(coverage_summary.get('branch_coverage'))}

        ## 2. 本次变更影响范围
        {changed_file_lines}

        ## 3. 单测结果分析
        - 用例总数：{junit_summary['total']}
        - 失败：{junit_summary['failures']}
        - 错误：{junit_summary['errors']}
        - 跳过：{junit_summary['skipped']}

        {failed_case_lines}

        ## 4. 覆盖率分析
        - 行覆盖率：{percent(coverage_summary.get('line_coverage'))}
        - 分支覆盖率：{percent(coverage_summary.get('branch_coverage'))}
        - 方法覆盖率：{percent(coverage_summary.get('method_coverage'))}

        {low_class_lines}

        ## 5. 接口覆盖率提升建议
        - 当前未接入接口自动化覆盖率结果，建议下一阶段补充 `quality/api_inventory.json` 与接口测试结果汇总。
        - 对本次变更涉及的 Controller、Service、Mapper 优先补充正常路径、鉴权失败、参数边界、幂等与异常分支用例。

        ## 6. 线上 Bug 风险预判
        - 优先关注本次变更文件对应的业务链路。
        - 如线上报错，优先按 traceId、接口路径、异常类名、错误码、业务主键检索日志。
        - 对低覆盖率类和单测失败类进行上线前复查。

        ## 7. 上线前检查清单
        - [ ] Jenkins 单测报告无失败和错误
        - [ ] JaCoCo 覆盖率达到本阶段门禁
        - [ ] 本次变更涉及的核心接口有自动化或人工验证记录
        - [ ] 低覆盖率类已有补测计划或风险接受记录
        - [ ] 上线后日志关键字和 traceId 查询方式已确认

        ## 附录：Diff 统计

        ```text
        {diff_stat or "未采集到 diff 统计"}
        ```
        """
    ).strip() + "\n"


def write_report(output_path: str, report_content: str) -> None:
    Path(output_path).write_text(report_content, encoding="utf-8")
    logger.info("Risk report generated: %s", output_path)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate a CI risk analysis report")
    parser.add_argument("--project", required=True, help="Project name")
    parser.add_argument("--diff-file", required=True, help="Git diff patch path")
    parser.add_argument("--diff-stat", required=True, help="Git diff stat path")
    parser.add_argument("--junit-report", required=True, help="JUnit report directory")
    parser.add_argument("--jacoco-xml", required=True, help="JaCoCo XML report path")
    parser.add_argument("--output", required=True, help="Output Markdown report path")
    parser.add_argument("--fail-on-ai-error", action="store_true", help="Exit non-zero when remote AI call fails")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    logger.info("Reading CI analysis inputs")

    diff_stat = read_text_file(args.diff_stat, max_chars=10000)
    diff_content = read_text_file(args.diff_file, max_chars=DEFAULT_MAX_DIFF_CHARS)
    junit_summary = parse_junit_reports(args.junit_report)
    coverage_summary = parse_jacoco_xml(args.jacoco_xml)
    changed_files = extract_changed_files(diff_stat, diff_content)
    prompt = build_prompt(args.project, diff_stat, diff_content, junit_summary, coverage_summary, changed_files)

    try:
        logger.info("Calling remote AI")
        report = call_ai(prompt)
        write_report(args.output, report)
        return 0
    except Exception as exc:
        reason = redact_sensitive(str(exc))
        logger.warning("Remote AI risk analysis skipped or failed: %s", reason)
        report = local_risk_report(args.project, diff_stat, junit_summary, coverage_summary, changed_files, reason)
        write_report(args.output, report)
        return 1 if args.fail_on_ai_error else 0


if __name__ == "__main__":
    raise SystemExit(main())
