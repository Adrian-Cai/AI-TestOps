 Jenkins CI/CD + 单测覆盖率 + AI 分析报告 + 邮件通知 + 线上 Bug 定位方案
两个阶段：
代码提交 / 合并请求
        ↓
Jenkins 拉代码
        ↓
编译 + 单测 + 覆盖率统计
        ↓
覆盖率门禁 / 失败拦截
        ↓
调用 AI 分析：变更 Diff + 单测结果 + 覆盖率报告
        ↓
生成上线风险报告
        ↓
邮件发送给你
        ↓
通过后部署

一、先装的 Jenkins 插件
建议安装这些：
插件	作用
Pipeline	写 Jenkinsfile 流水线
Git Plugin	拉取 Git 仓库代码
JUnit Plugin	收集单测结果
JaCoCo Plugin / coverage	收集代码覆盖率
HTML Publisher Plugin	展示覆盖率 HTML 报告
Email Extension Plugin	发邮件
Credentials Plugin	管理 Git、邮箱、AI API Key
Jenkins 官方的 JaCoCo Pipeline 步骤支持记录覆盖率报告，并且可以配置覆盖率阈值，让构建结果变成 unstable 或失败。(Jenkins)
HTML Publisher 插件可以把构建产物里的 HTML 报告发布到 Jenkins Job 页面。(Jenkins Plugins)
Coverage Plugin 也支持收集多种覆盖率报告，并能和 SCM 平台集成展示覆盖率摘要。(Jenkins Plugins)

二、项目侧先加 JaCoCo 覆盖率配置
以 Maven + Spring Boot 为例。
1. pom.xml 加 JaCoCo
<build>
    <plugins>
        <!-- 单元测试执行插件 -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <testFailureIgnore>false</testFailureIgnore>
            </configuration>
        </plugin>
        <!-- JaCoCo 覆盖率插件 -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.12</version>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.70</minimum>
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>

这一步的作用是：
mvn test
    ↓
执行单测
    ↓
生成 target/site/jacoco/index.html
    ↓
生成 target/site/jacoco/jacoco.xml
    ↓
覆盖率低于 70% 时失败
JaCoCo 是 Java 常用的代码覆盖率工具，Gradle 官方文档也说明其 JaCoCo 插件用于为 Java 代码生成覆盖率指标。(docs.gradle.org)

三、Jenkinsfile 配置：单测 + 覆盖率 + AI 分析 + 邮件
可以直接改造的 Jenkinsfile。
Jenkinsfile
pipeline {
    agent any

    environment {
        PROJECT_NAME = 'ai-testops-demo'
        REPORT_DIR = 'target/site/jacoco'
        JUNIT_REPORT = 'target/surefire-reports/*.xml'
        JACOCO_XML = 'target/site/jacoco/jacoco.xml'
        AI_REPORT = 'ai-risk-report.md'

        // Jenkins Credentials 里配置：
        // OPENAI_API_KEY: Secret Text
        OPENAI_API_KEY = credentials('OPENAI_API_KEY')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Diff') {
            steps {
                sh '''
                    echo "生成本次变更 Diff..."
                    git fetch origin master || true

                    if git rev-parse --verify origin/master >/dev/null 2>&1; then
                        git diff origin/master...HEAD --stat > diff-stat.txt
                        git diff origin/master...HEAD > diff-full.patch
                    else
                        git diff HEAD~1...HEAD --stat > diff-stat.txt
                        git diff HEAD~1...HEAD > diff-full.patch
                    fi

                    echo "Diff 统计："
                    cat diff-stat.txt
                '''
            }
        }

        stage('Unit Test') {
            steps {
                sh '''
                    echo "开始执行单元测试..."
                    mvn clean test
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: "${JUNIT_REPORT}"
                }
            }
        }

        stage('Coverage Report') {
            steps {
                sh '''
                    echo "生成 JaCoCo 覆盖率报告..."
                    mvn jacoco:report
                '''

                publishHTML(target: [
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: "${REPORT_DIR}",
                    reportFiles: 'index.html',
                    reportName: 'JaCoCo Coverage Report'
                ])
            }
        }

        stage('Coverage Gate') {
            steps {
                sh '''
                    echo "执行覆盖率门禁..."
                    mvn jacoco:check
                '''
            }
        }

        stage('AI Risk Analysis') {
            steps {
                sh '''
                    echo "开始调用 AI 生成上线风险分析报告..."

                    python3 scripts/ai_ci_analyzer.py \
                      --project "${PROJECT_NAME}" \
                      --diff-file diff-full.patch \
                      --diff-stat diff-stat.txt \
                      --junit-report "target/surefire-reports" \
                      --jacoco-xml "${JACOCO_XML}" \
                      --output "${AI_REPORT}"
                '''
            }
        }

        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: '''
                    diff-stat.txt,
                    diff-full.patch,
                    ai-risk-report.md,
                    target/site/jacoco/**,
                    target/surefire-reports/**
                ''', fingerprint: true
            }
        }

        stage('Deploy') {
            when {
                branch 'master'
            }
            steps {
                sh '''
                    echo "这里执行部署脚本..."
                    # sh deploy.sh
                '''
            }
        }
    }

    post {
        always {
            emailext(
                subject: "[${PROJECT_NAME}] Jenkins 构建结果：${currentBuild.currentResult} - #${env.BUILD_NUMBER}",
                to: "imacaiy@gmail.com",
                mimeType: 'text/html',
                body: """
                    <h2>${PROJECT_NAME} 构建结果：${currentBuild.currentResult}</h2>
                    <p><b>构建编号：</b>#${env.BUILD_NUMBER}</p>
                    <p><b>分支：</b>${env.BRANCH_NAME}</p>
                    <p><b>构建地址：</b><a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
                    <h3>核心报告</h3>
                    <ul>
                        <li><a href="${env.BUILD_URL}artifact/ai-risk-report.md">AI 风险分析报告</a></li>
                        <li><a href="${env.BUILD_URL}JaCoCo_20Coverage_20Report/">JaCoCo 覆盖率报告</a></li>
                        <li><a href="${env.BUILD_URL}testReport/">JUnit 单测报告</a></li>
                    </ul>
                    <p>如本次构建失败，请优先查看 AI 风险分析报告中的：</p>
                    <ul>
                        <li>失败单测对应模块</li>
                        <li>覆盖率下降接口</li>
                        <li>Diff 涉及业务流</li>
                        <li>建议补充的测试场景</li>
                    </ul>
                """
            )
        }
    }
}

四、AI 分析脚本：scripts/ai_ci_analyzer.py
这个脚本负责把这些内容喂给 AI：
1. 本次代码 Diff
2. Diff 文件统计
3. JUnit 单测结果
4. JaCoCo 覆盖率 XML
5. 让 AI 输出：
   - 影响模块
   - 风险业务流
   - 覆盖率不足点
   - 建议补充单测
   - 上线风险等级
   - 是否建议阻断上线
OpenAI 官方文档支持 Structured Outputs，可以让模型按 JSON Schema 返回结构化结果，适合 CI/CD 场景中做自动化分析和门禁判断。(OpenAI开发者)
scripts/ai_ci_analyzer.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
脚本名称：ai_ci_analyzer.py

功能：
1. 读取 Git Diff、JUnit 单测结果、JaCoCo 覆盖率报告。
2. 调用 AI 分析本次变更风险。
3. 生成 Markdown 格式的上线风险报告。
4. 用于 Jenkins CI/CD 流水线中的上线前质量分析。

使用示例：
python3 scripts/ai_ci_analyzer.py \
  --project ai-testops-demo \
  --diff-file diff-full.patch \
  --diff-stat diff-stat.txt \
  --junit-report target/surefire-reports \
  --jacoco-xml target/site/jacoco/jacoco.xml \
  --output ai-risk-report.md
"""

import argparse
import json
import logging
import os
import sys
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Any

from openai import OpenAI


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)

logger = logging.getLogger(__name__)


def read_text_file(file_path: str, max_chars: int = 60000) -> str:
    """
    读取文本文件，并限制最大字符数，避免超出模型上下文。
    """
    path = Path(file_path)

    if not path.exists():
        logger.warning("文件不存在：%s", file_path)
        return ""

    content = path.read_text(encoding="utf-8", errors="ignore")

    if len(content) > max_chars:
        logger.warning("文件内容过长，已截断：%s", file_path)
        return content[:max_chars] + "\n\n[内容过长，已截断]"

    return content


def parse_junit_reports(report_dir: str) -> Dict[str, Any]:
    """
    解析 JUnit XML 报告，提取测试数量、失败数量、失败用例。
    """
    result = {
        "total": 0,
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "failed_cases": []
    }

    report_path = Path(report_dir)

    if not report_path.exists():
        logger.warning("JUnit 报告目录不存在：%s", report_dir)
        return result

    for xml_file in report_path.glob("*.xml"):
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()

            testcases = root.findall(".//testcase")
            result["total"] += len(testcases)

            for case in testcases:
                failure = case.find("failure")
                error = case.find("error")
                skipped = case.find("skipped")

                if skipped is not None:
                    result["skipped"] += 1

                if failure is not None or error is not None:
                    if failure is not None:
                        result["failures"] += 1
                        message = failure.attrib.get("message", "")
                    else:
                        result["errors"] += 1
                        message = error.attrib.get("message", "")

                    result["failed_cases"].append({
                        "class_name": case.attrib.get("classname", ""),
                        "test_name": case.attrib.get("name", ""),
                        "message": message[:500]
                    })

        except Exception as exc:
            logger.exception("解析 JUnit 报告失败：%s，错误：%s", xml_file, exc)

    return result


def parse_jacoco_xml(jacoco_xml: str) -> Dict[str, Any]:
    """
    解析 JaCoCo XML 报告，提取整体覆盖率和低覆盖率类。
    """
    result = {
        "line_coverage": None,
        "branch_coverage": None,
        "method_coverage": None,
        "low_coverage_classes": []
    }

    path = Path(jacoco_xml)

    if not path.exists():
        logger.warning("JaCoCo XML 不存在：%s", jacoco_xml)
        return result

    try:
        tree = ET.parse(path)
        root = tree.getroot()

        counters = root.findall("counter")

        for counter in counters:
            counter_type = counter.attrib.get("type")
            missed = int(counter.attrib.get("missed", 0))
            covered = int(counter.attrib.get("covered", 0))
            total = missed + covered

            ratio = round(covered / total, 4) if total > 0 else None

            if counter_type == "LINE":
                result["line_coverage"] = ratio
            elif counter_type == "BRANCH":
                result["branch_coverage"] = ratio
            elif counter_type == "METHOD":
                result["method_coverage"] = ratio

        for package in root.findall("package"):
            package_name = package.attrib.get("name", "")

            for clazz in package.findall("class"):
                class_name = clazz.attrib.get("name", "")
                line_counter = None

                for counter in clazz.findall("counter"):
                    if counter.attrib.get("type") == "LINE":
                        missed = int(counter.attrib.get("missed", 0))
                        covered = int(counter.attrib.get("covered", 0))
                        total = missed + covered
                        ratio = round(covered / total, 4) if total > 0 else None
                        line_counter = ratio

                if line_counter is not None and line_counter < 0.6:
                    result["low_coverage_classes"].append({
                        "class_name": f"{package_name}/{class_name}",
                        "line_coverage": line_counter
                    })

    except Exception as exc:
        logger.exception("解析 JaCoCo XML 失败：%s", exc)

    return result


def build_prompt(
    project: str,
    diff_stat: str,
    diff_content: str,
    junit_summary: Dict[str, Any],
    coverage_summary: Dict[str, Any]
) -> str:
    """
    构造 AI 分析 Prompt。
    """
    return f"""
你是一名资深测试架构师和质量保障负责人。

请基于以下 CI/CD 信息，生成一份上线前风险分析报告。

项目名称：
{project}

一、本次代码 Diff 统计：
{diff_stat}

二、本次代码 Diff 内容：
{diff_content}

三、JUnit 单测结果：
{json.dumps(junit_summary, ensure_ascii=False, indent=2)}

四、JaCoCo 覆盖率结果：
{json.dumps(coverage_summary, ensure_ascii=False, indent=2)}

请输出 Markdown 报告，结构必须包含：

# AI 上线风险分析报告

## 1. 结论摘要
- 本次变更风险等级：低 / 中 / 高
- 是否建议上线：建议上线 / 谨慎上线 / 不建议上线
- 核心原因

## 2. 本次变更影响范围
- 影响模块
- 影响接口
- 影响业务流
- 可能受影响的上下游

## 3. 单测结果分析
- 失败用例
- 失败原因推测
- 对业务风险的影响

## 4. 覆盖率分析
- 当前整体覆盖率
- 低覆盖率类
- 需要重点补充的测试点

## 5. 接口覆盖率提升建议
请按接口维度输出：
- 接口名称或推测接口
- 当前风险
- 建议补充的单测
- 建议补充的异常场景
- 建议补充的边界值

## 6. 线上 Bug 风险预判
请结合 Diff 判断：
- 哪些业务流最容易出问题
- 如果线上报错，优先排查哪些类、方法、接口
- 需要关注哪些日志关键词

## 7. 上线前检查清单
输出可执行 checklist。

注意：
1. 不要泛泛而谈。
2. 必须结合 Diff、单测、覆盖率进行分析。
3. 如果信息不足，要明确说明“不足在哪里”。
4. 输出要适合直接发邮件给测试、研发、项目负责人。
"""


def call_ai(prompt: str) -> str:
    """
    调用 AI 模型生成分析报告。
    """
    api_key = os.getenv("OPENAI_API_KEY")

    if not api_key:
        raise RuntimeError("环境变量 OPENAI_API_KEY 未配置")

    client = OpenAI(api_key=api_key)

    response = client.responses.create(
        model="gpt-4.1-mini",
        input=prompt
    )

    return response.output_text


def write_report(output_path: str, report_content: str) -> None:
    """
    写入 Markdown 报告。
    """
    Path(output_path).write_text(report_content, encoding="utf-8")
    logger.info("AI 风险报告已生成：%s", output_path)


def main() -> None:
    parser = argparse.ArgumentParser(description="CI/CD AI 风险分析工具")

    parser.add_argument("--project", required=True, help="项目名称")
    parser.add_argument("--diff-file", required=True, help="Git Diff 文件路径")
    parser.add_argument("--diff-stat", required=True, help="Git Diff 统计文件路径")
    parser.add_argument("--junit-report", required=True, help="JUnit 报告目录")
    parser.add_argument("--jacoco-xml", required=True, help="JaCoCo XML 报告路径")
    parser.add_argument("--output", required=True, help="输出 Markdown 报告路径")

    args = parser.parse_args()

    logger.info("开始读取 CI/CD 分析数据")

    diff_stat = read_text_file(args.diff_stat, max_chars=10000)
    diff_content = read_text_file(args.diff_file, max_chars=60000)
    junit_summary = parse_junit_reports(args.junit_report)
    coverage_summary = parse_jacoco_xml(args.jacoco_xml)

    prompt = build_prompt(
        project=args.project,
        diff_stat=diff_stat,
        diff_content=diff_content,
        junit_summary=junit_summary,
        coverage_summary=coverage_summary
    )

    logger.info("开始调用 AI 生成报告")
    report = call_ai(prompt)

    write_report(args.output, report)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        logger.exception("AI CI 分析失败：%s", exc)

        fallback_report = textwrap.dedent(f"""
        # AI 上线风险分析报告生成失败

        ## 失败原因

        {str(exc)}

        ## 建议处理

        1. 检查 OPENAI_API_KEY 是否配置。
        2. 检查 diff-full.patch 是否存在。
        3. 检查 target/surefire-reports 是否存在。
        4. 检查 target/site/jacoco/jacoco.xml 是否存在。
        5. 检查 Jenkins 节点是否可以访问 AI API。
        """)

        output = "ai-risk-report.md"

        if "--output" in sys.argv:
            try:
                output = sys.argv[sys.argv.index("--output") + 1]
            except Exception:
                pass

        Path(output).write_text(fallback_report, encoding="utf-8")
        sys.exit(1)

五、怎么“提高每个接口覆盖率”
这里要先纠正一个点：
JaCoCo 统计的是代码覆盖率，不是接口覆盖率。
接口覆盖率要单独设计。建议你分成三层：
接口覆盖率 = 接口被测试覆盖
代码覆盖率 = 代码行 / 分支 / 方法被执行覆盖
业务流覆盖率 = 核心业务链路被场景覆盖
你可以在 Jenkins 里同时采集这三类结果。
推荐覆盖率模型
覆盖类型	工具/方式	示例
单测覆盖率	JaCoCo	Service、Mapper、Utils
接口覆盖率	API 自动化用例 / Postman / Newman / pytest / RestAssured	/api/order/create 是否有用例
业务流覆盖率	测试用例平台 / 自定义 JSON	下单 → 支付 → 核销 → 退款

六、接口覆盖率怎么落库
维护一份接口清单，例如：
api_inventory.json
[
  {
    "api": "POST /api/order/create",
    "module": "order",
    "business_flow": "下单流程",
    "owner": "order-service"
  },
  {
    "api": "POST /api/order/pay",
    "module": "payment",
    "business_flow": "支付流程",
    "owner": "payment-service"
  },
  {
    "api": "POST /api/order/refund",
    "module": "refund",
    "business_flow": "退款流程",
    "owner": "refund-service"
  }
]
然后接口自动化用例执行后生成：
api_test_result.json
[
  {
    "api": "POST /api/order/create",
    "case_count": 5,
    "passed": 5,
    "failed": 0
  },
  {
    "api": "POST /api/order/pay",
    "case_count": 3,
    "passed": 3,
    "failed": 0
  }
]
最后计算：
接口总数：3
已覆盖接口：2
接口覆盖率：66.67%
未覆盖接口：
- POST /api/order/refund
这个结果也可以交给 AI，让 AI 输出：
本次 Diff 涉及 refund 模块，但 POST /api/order/refund 没有接口自动化覆盖。
建议上线前补充：
1. 正常退款
2. 重复退款
3. 超时退款
4. 金额边界
5. 订单状态非法

七、线上 Bug 快速定位：关键不是 AI，而是“日志 + 链路 + 业务流映射”
线上 bug 我要你快速给我定位到哪个业务流出了问题
这个不能只靠 AI 猜，必须提前把系统打点和映射做好。
设计一个 业务流画像表。
1. 业务流映射表
[
  {
    "business_flow": "下单流程",
    "apis": [
      "POST /api/order/create",
      "POST /api/coupon/lock",
      "POST /api/inventory/deduct"
    ],
    "services": [
      "order-service",
      "coupon-service",
      "inventory-service"
    ],
    "core_logs": [
      "ORDER_CREATE_START",
      "COUPON_LOCK_FAILED",
      "INVENTORY_DEDUCT_FAILED"
    ]
  },
  {
    "business_flow": "支付流程",
    "apis": [
      "POST /api/pay/create",
      "POST /api/pay/callback"
    ],
    "services": [
      "payment-service",
      "order-service"
    ],
    "core_logs": [
      "PAY_CREATE_START",
      "PAY_CALLBACK_RECEIVED",
      "ORDER_PAY_STATUS_UPDATE_FAILED"
    ]
  }
]
2. 日志必须带这些字段
每个核心接口建议统一日志格式：
{
  "traceId": "abc-123",
  "userId": "10001",
  "businessFlow": "支付流程",
  "api": "POST /api/pay/callback",
  "service": "payment-service",
  "method": "PayCallbackService.handleCallback",
  "orderId": "O202605180001",
  "errorCode": "ORDER_STATUS_INVALID",
  "message": "订单状态非法，无法完成支付回调"
}
这样线上出 Bug 时，AI 才能根据日志快速判断：
traceId = abc-123
错误接口 = POST /api/pay/callback
错误服务 = payment-service
错误方法 = PayCallbackService.handleCallback
错误码 = ORDER_STATUS_INVALID
归属业务流 = 支付流程
疑似原因 = 支付回调时订单状态已变更，可能存在重复回调、状态流转异常或并发更新问题
优先排查 = 订单状态机、支付回调幂等、订单表状态更新时间

八、线上 Bug 定位脚本思路
做一个脚本：
输入：
1. traceId
2. 错误日志
3. 业务流映射表
4. 最近一次上线 Diff
5. 最近一次 AI 风险报告

输出：
1. 属于哪个业务流
2. 哪个接口异常
3. 哪个服务异常
4. 哪段代码最近改过
5. 是否命中上线风险
6. 建议研发优先排查路径
命名可以叫：
online_bug_locator.py
分析流程：
线上报错日志
    ↓
提取 traceId / api / errorCode / service / method
    ↓
匹配 business_flow_map.json
    ↓
匹配最近一次上线 diff-full.patch
    ↓
匹配最近一次 ai-risk-report.md
    ↓
AI 生成定位报告

九、推荐最终目录结构
project-root
├── Jenkinsfile
├── pom.xml
├── scripts
│   ├── ai_ci_analyzer.py
│   ├── api_coverage_analyzer.py
│   └── online_bug_locator.py
├── quality
│   ├── api_inventory.json
│   ├── business_flow_map.json
│   └── quality_gate.yaml
├── target
│   ├── surefire-reports
│   └── site
│       └── jacoco
└── ai-risk-report.md

十、建议Jenkins 质量门禁这样设
第一阶段：先能跑通
quality_gate:
  unit_test:
    allow_failed: false

  jacoco:
    line_coverage_min: 0.60
    branch_coverage_min: 0.40

  api_coverage:
    min: 0.50

  ai_risk:
    block_when_high_risk: false
第二阶段：开始拦截风险
quality_gate:
  unit_test:
    allow_failed: false

  jacoco:
    line_coverage_min: 0.70
    branch_coverage_min: 0.50

  api_coverage:
    min: 0.70

  ai_risk:
    block_when_high_risk: true
第三阶段：做增量覆盖率
不要只看全量覆盖率，而是看：
本次 Diff 改动代码的覆盖率
也就是：
全量覆盖率 70%
但本次改动代码覆盖率只有 20%
这种情况应该拦截。

十一、你这个方案的核心价值
要做的不是“Jenkins 调 AI”，而是：
代码变更
    ↓
自动执行测试
    ↓
自动计算覆盖率
    ↓
自动判断风险
    ↓
自动生成上线报告
    ↓
线上出问题时，反向定位业务流
这套东西非常适合你现在做的 AI TestOps / 测试平台方向。
你可以把它包装成一个平台能力：
AI 上线前风险分析
核心能力包括：
能力	说明
Diff 分析	分析本次改动文件、类、方法
单测分析	识别失败用例和失败模块
覆盖率分析	判断新增代码是否被覆盖
接口覆盖分析	判断相关接口是否有自动化用例
业务流映射	判断影响哪个业务链路
AI 报告	输出上线风险、测试建议、排查路径
邮件通知	发给测试、研发、负责人
线上定位	根据日志和 traceId 反查业务流

十二、最小可落地版本
可以先只做这个版本：
Jenkinsfile
    ↓
mvn clean test
    ↓
mvn jacoco:report
    ↓
git diff 生成 diff-full.patch
    ↓
调用 ai_ci_analyzer.py
    ↓
生成 ai-risk-report.md
    ↓
发邮件到你的邮箱
第一版先不要做太复杂。
后面再加：
接口覆盖率
业务流映射
线上 Bug 定位
Diff 增量覆盖率
AI 自动补充用例建议
这样落地节奏比较稳

十三、当前仓库第一版落地方式
本仓库已先落地最小可跑版本，避免影响现有部署流水线。

已新增 / 修改：
- `pom.xml`：加入 `maven-surefire-plugin` 与 `jacoco-maven-plugin`，`mvn test` 会生成 `target/site/jacoco/jacoco.xml` 和 HTML 覆盖率报告。
- `scripts/ai_ci_analyzer.py`：读取 diff、JUnit、JaCoCo，生成 `ai-risk-report.md`。
- `scripts/tests/test_ai_ci_analyzer.py`：覆盖脚本的 JUnit、JaCoCo 解析逻辑。
- `Jenkinsfile.ai-risk`：独立的 Jenkins 质量分析流水线，不覆盖当前部署用 `Jenkinsfile`。

Jenkins 新建任务建议：
1. 新建 Pipeline 任务，例如 `ai-testops-risk-analysis`。
2. Pipeline 选择 `Pipeline script from SCM`。
3. 仓库地址使用当前项目仓库。
4. Script Path 填：`Jenkinsfile.ai-risk`。
5. 第一次运行保持 `AI_PROVIDER=MOCK`，先确认单测、覆盖率、归档和邮件链路能跑通。
6. 跑通后，在 Jenkins Credentials 中增加 Secret text：
   - ID：`AI_API_KEY`
   - Secret：你的 OpenAI-compatible API Key
7. 第二次运行时把 `AI_PROVIDER` 改为 `OPENAI_COMPATIBLE`，按实际情况设置：
   - `AI_API_BASE`：例如 `https://api.openai.com`
   - `AI_MODEL_NAME`：例如 `gpt-4o-mini`
   - `AI_CREDENTIALS_ID`：默认 `AI_API_KEY`
8. 如需邮件通知，填写 `EMAIL_TO`；留空则只归档报告，不发邮件。

第一阶段建议参数：
```text
AI_PROVIDER=MOCK
COVERAGE_GATE_STRICT=false
LINE_COVERAGE_MIN=0.60
BRANCH_COVERAGE_MIN=0.40
FAIL_ON_AI_ERROR=false
```

本地验证命令：
```bash
python -m unittest scripts.tests.test_ai_ci_analyzer
mvn -B clean test
mvn -B jacoco:check -Djacoco.line.coverage.minimum=0.60 -Djacoco.branch.coverage.minimum=0.40
```

注意：
- `AI_PROVIDER=MOCK` 时不会调用外部模型，会输出本地规则版风险报告，适合首次联调。
- 远程 AI 调用失败时，默认仍会生成降级报告；如果希望失败即阻断，把 `FAIL_ON_AI_ERROR=true`。
- 当前版本先做代码覆盖率与测试结果分析；接口覆盖率、业务流映射、线上 Bug 定位建议作为第二阶段继续补充。
