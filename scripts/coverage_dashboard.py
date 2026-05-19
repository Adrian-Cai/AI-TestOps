#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""Generate a polished HTML dashboard from a JaCoCo XML report."""

from __future__ import annotations

import argparse
import html
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


EXEMPT_HINTS: tuple[tuple[str, str], ...] = (
    ("/entity/", "Entity/POJO 主要是数据承载，优先用业务服务测试间接覆盖"),
    ("/vo/", "VO 主要是接口展示结构，除自定义转换逻辑外不单独追覆盖率"),
    ("/dto/", "DTO 主要是入参结构，校验规则由 Controller/API 测试覆盖"),
    ("/enums/", "枚举常量没有复杂分支，不作为覆盖率提升重点"),
    ("/mapper/", "MyBatis Mapper 接口由集成测试或数据库契约测试覆盖"),
    ("/config/", "配置装配类优先用启动/集成测试兜底，不追逐行覆盖"),
)

MUST_COVER_HINTS: tuple[tuple[str, str], ...] = (
    ("/controller/", "Controller 是接口契约入口，必须覆盖成功、校验失败和异常响应"),
    ("/service/", "Service 承载业务规则，必须覆盖正常流、异常流和边界值"),
    ("/risk/", "风险识别规则会影响合并准入结论，必须覆盖核心分支"),
    ("/coverage/", "覆盖匹配会影响补测判断，必须覆盖匹配、未匹配和回退策略"),
    ("/gate/", "门禁计算会影响上线阻断，必须覆盖 PASS/WARNING/BLOCK/人工复核"),
    ("/parser/", "文档解析会影响后续 AI 输入质量，必须覆盖清洗、分块和异常输入"),
    ("/export/", "导出结果直接交付给用户，必须覆盖格式和空数据场景"),
    ("/client/", "外部客户端必须覆盖请求构造、失败响应和敏感信息脱敏"),
    ("/util/", "工具类如果处理 ID、JSON、文件名、Hash、TraceId，必须覆盖边界输入"),
)


@dataclass(frozen=True)
class Counter:
    missed: int = 0
    covered: int = 0

    @property
    def total(self) -> int:
        return self.missed + self.covered

    @property
    def ratio(self) -> float | None:
        if self.total == 0:
            return None
        return round(self.covered / self.total, 4)


@dataclass(frozen=True)
class CoverageClass:
    name: str
    package_name: str
    line: Counter
    branch: Counter
    method: Counter
    instruction: Counter
    priority: str
    reason: str


@dataclass(frozen=True)
class CoverageReport:
    project: str
    source_xml: str
    line: Counter
    branch: Counter
    method: Counter
    instruction: Counter
    classes: list[CoverageClass]


def counter_from(node: ET.Element | None) -> Counter:
    if node is None:
        return Counter()
    return Counter(
        missed=int(node.attrib.get("missed", 0) or 0),
        covered=int(node.attrib.get("covered", 0) or 0),
    )


def find_counter(node: ET.Element, counter_type: str) -> Counter:
    return counter_from(next((item for item in node.findall("counter") if item.attrib.get("type") == counter_type), None))


def classify_class(class_name: str) -> tuple[str, str]:
    normalized = "/" + class_name.replace("\\", "/").strip("/") + "/"
    simple_name = class_name.rsplit("/", 1)[-1]
    if simple_name.endswith("Application"):
        return "exempt", "Spring Boot 启动入口由启动测试验证即可"
    for pattern, reason in EXEMPT_HINTS:
        if pattern in normalized:
            return "exempt", reason
    for pattern, reason in MUST_COVER_HINTS:
        if pattern in normalized:
            return "must", reason
    return "normal", "普通实现类，结合变更频率和业务风险安排补测"


def qualified_class_name(package_name: str, raw_name: str) -> str:
    if not package_name or raw_name.startswith(f"{package_name}/"):
        return raw_name
    return f"{package_name}/{raw_name}".strip("/")


def parse_jacoco_xml(jacoco_xml: str, project: str) -> CoverageReport:
    path = Path(jacoco_xml)
    root = ET.parse(path).getroot()
    classes: list[CoverageClass] = []

    for package in root.findall("package"):
        package_name = package.attrib.get("name", "")
        for clazz in package.findall("class"):
            raw_name = clazz.attrib.get("name", "")
            class_name = qualified_class_name(package_name, raw_name)
            line = find_counter(clazz, "LINE")
            branch = find_counter(clazz, "BRANCH")
            method = find_counter(clazz, "METHOD")
            instruction = find_counter(clazz, "INSTRUCTION")
            if line.total == 0 and method.total == 0:
                priority, reason = "exempt", "接口或空契约没有可执行行，不作为覆盖率补测目标"
            else:
                priority, reason = classify_class(class_name)
            classes.append(
                CoverageClass(
                    name=class_name,
                    package_name=package_name,
                    line=line,
                    branch=branch,
                    method=method,
                    instruction=instruction,
                    priority=priority,
                    reason=reason,
                )
            )

    return CoverageReport(
        project=project,
        source_xml=str(path),
        line=find_counter(root, "LINE"),
        branch=find_counter(root, "BRANCH"),
        method=find_counter(root, "METHOD"),
        instruction=find_counter(root, "INSTRUCTION"),
        classes=classes,
    )


def percent(value: float | None) -> str:
    if value is None:
        return "N/A"
    return f"{value * 100:.2f}%"


def status_for(ratio: float | None, target: float) -> str:
    if ratio is None:
        return "unknown"
    if ratio >= target:
        return "good"
    if ratio >= target * 0.8:
        return "warn"
    return "bad"


def package_rows(classes: Iterable[CoverageClass]) -> list[tuple[str, Counter, Counter, int]]:
    totals: dict[str, dict[str, int]] = {}
    for clazz in classes:
        item = totals.setdefault(clazz.package_name or "(default)", {"line_missed": 0, "line_covered": 0, "branch_missed": 0, "branch_covered": 0, "count": 0})
        item["line_missed"] += clazz.line.missed
        item["line_covered"] += clazz.line.covered
        item["branch_missed"] += clazz.branch.missed
        item["branch_covered"] += clazz.branch.covered
        item["count"] += 1
    rows = [
        (
            name,
            Counter(values["line_missed"], values["line_covered"]),
            Counter(values["branch_missed"], values["branch_covered"]),
            values["count"],
        )
        for name, values in totals.items()
    ]
    return sorted(rows, key=lambda row: (row[1].ratio is None, row[1].ratio or 0))


def table_row(cells: Iterable[str], class_name: str = "") -> str:
    classes = f' class="{class_name}"' if class_name else ""
    return "<tr%s>%s</tr>" % (classes, "".join(f"<td>{cell}</td>" for cell in cells))


def class_rows(classes: Iterable[CoverageClass], line_min: float, limit: int | None = None) -> str:
    selected = list(classes)
    if limit is not None:
        selected = selected[:limit]
    if not selected:
        return '<tr><td colspan="6" class="muted">暂无数据</td></tr>'
    rows = []
    for clazz in selected:
        state = status_for(clazz.line.ratio, line_min)
        badge = {
            "must": "必须覆盖",
            "normal": "常规关注",
            "exempt": "低优先级",
        }[clazz.priority]
        rows.append(
            table_row(
                [
                    f'<code>{html.escape(clazz.name)}</code>',
                    f'<span class="badge {clazz.priority}">{badge}</span>',
                    f'<span class="metric {state}">{percent(clazz.line.ratio)}</span>',
                    percent(clazz.branch.ratio),
                    str(clazz.line.missed),
                    html.escape(clazz.reason),
                ]
            )
        )
    return "\n".join(rows)


def package_table_rows(classes: Iterable[CoverageClass], line_min: float) -> str:
    rows = []
    for package_name, line, branch, count in package_rows(classes):
        rows.append(
            table_row(
                [
                    f'<code>{html.escape(package_name)}</code>',
                    str(count),
                    f'<span class="metric {status_for(line.ratio, line_min)}">{percent(line.ratio)}</span>',
                    percent(branch.ratio),
                    str(line.missed),
                ]
            )
        )
    return "\n".join(rows) or '<tr><td colspan="5" class="muted">暂无数据</td></tr>'


def write_missing_dashboard(project: str, jacoco_xml: str, output_dir: str) -> Path:
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    html_path = output / "index.html"
    html_path.write_text(
        f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>{html.escape(project)} 覆盖率看板</title>
  <style>
    body {{ margin: 0; font-family: Inter, "Segoe UI", "Microsoft YaHei", sans-serif; background: #f6f8fb; color: #172033; }}
    main {{ max-width: 960px; margin: 72px auto; padding: 0 24px; }}
    .panel {{ background: white; border: 1px solid #dbe3ef; border-radius: 8px; padding: 28px; box-shadow: 0 12px 36px rgba(21, 32, 52, .08); }}
    h1 {{ margin: 0 0 12px; font-size: 28px; }}
    p {{ line-height: 1.75; }}
    code {{ background: #eef3f8; padding: 2px 6px; border-radius: 4px; }}
  </style>
</head>
<body>
  <main>
    <section class="panel">
      <h1>{html.escape(project)} 覆盖率看板未生成</h1>
      <p>未找到 JaCoCo XML：<code>{html.escape(jacoco_xml)}</code>。</p>
      <p>通常是单测阶段没有完成、JaCoCo 插件未生成报告，或报告路径配置不一致。请先查看 Jenkins 单测日志和 <code>target/surefire-reports</code>。</p>
    </section>
  </main>
</body>
</html>
""",
        encoding="utf-8",
    )
    return html_path


def generate_dashboard(report: CoverageReport, output_dir: str, line_min: float, branch_min: float) -> Path:
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    html_path = output / "index.html"

    must_gaps = sorted(
        (item for item in report.classes if item.priority == "must" and (item.line.ratio or 0) < line_min),
        key=lambda item: item.line.ratio or 0,
    )
    low_classes = sorted(report.classes, key=lambda item: item.line.ratio or 0)[:20]
    exempt_classes = sorted((item for item in report.classes if item.priority == "exempt"), key=lambda item: item.name)[:30]
    must_count = sum(1 for item in report.classes if item.priority == "must")
    exempt_count = sum(1 for item in report.classes if item.priority == "exempt")

    html_path.write_text(
        f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>{html.escape(report.project)} 覆盖率看板</title>
  <style>
    :root {{
      --bg: #f4f7fb;
      --surface: #ffffff;
      --text: #172033;
      --muted: #667085;
      --border: #dbe3ef;
      --blue: #2563eb;
      --green: #138a4a;
      --orange: #b76508;
      --red: #c53030;
      --cyan: #0f766e;
    }}
    * {{ box-sizing: border-box; }}
    body {{ margin: 0; font-family: Inter, "Segoe UI", "Microsoft YaHei", sans-serif; background: var(--bg); color: var(--text); }}
    header {{ background: linear-gradient(135deg, #0f172a 0%, #184568 58%, #0f766e 100%); color: white; }}
    .hero {{ max-width: 1220px; margin: 0 auto; padding: 34px 24px 30px; }}
    .eyebrow {{ margin: 0 0 8px; color: #c7d2fe; font-size: 13px; letter-spacing: .08em; text-transform: uppercase; }}
    h1 {{ margin: 0; font-size: 32px; line-height: 1.2; letter-spacing: 0; }}
    .hero p {{ max-width: 820px; margin: 12px 0 0; color: #dbeafe; line-height: 1.7; }}
    main {{ max-width: 1220px; margin: 0 auto; padding: 24px; display: grid; gap: 18px; }}
    .grid {{ display: grid; gap: 14px; grid-template-columns: repeat(4, minmax(0, 1fr)); }}
    .card {{ background: var(--surface); border: 1px solid var(--border); border-radius: 8px; box-shadow: 0 8px 28px rgba(21, 32, 52, .06); }}
    .kpi {{ padding: 18px; min-height: 128px; display: grid; align-content: space-between; }}
    .kpi span {{ color: var(--muted); font-size: 13px; }}
    .kpi strong {{ display: block; margin-top: 10px; font-size: 31px; line-height: 1; }}
    .kpi small {{ color: var(--muted); }}
    section.card {{ padding: 20px; }}
    h2 {{ margin: 0 0 14px; font-size: 19px; letter-spacing: 0; }}
    .section-lead {{ margin: -6px 0 16px; color: var(--muted); line-height: 1.7; }}
    table {{ width: 100%; border-collapse: collapse; table-layout: fixed; }}
    th, td {{ padding: 11px 12px; border-bottom: 1px solid #edf1f6; text-align: left; vertical-align: top; font-size: 13px; }}
    th {{ color: #344054; background: #f8fafc; font-weight: 700; }}
    code {{ word-break: break-all; color: #1f2937; background: #f3f6fa; padding: 2px 5px; border-radius: 4px; }}
    .badge {{ display: inline-flex; align-items: center; min-height: 24px; padding: 2px 8px; border-radius: 999px; font-weight: 700; font-size: 12px; }}
    .badge.must {{ color: #9f1239; background: #fff1f2; }}
    .badge.normal {{ color: #1d4ed8; background: #eff6ff; }}
    .badge.exempt {{ color: #475467; background: #f2f4f7; }}
    .metric {{ font-weight: 800; }}
    .metric.good {{ color: var(--green); }}
    .metric.warn {{ color: var(--orange); }}
    .metric.bad {{ color: var(--red); }}
    .metric.unknown {{ color: var(--muted); }}
    .strategy {{ display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }}
    .strategy div {{ border: 1px solid var(--border); border-radius: 8px; padding: 14px; background: #fbfdff; }}
    .strategy h3 {{ margin: 0 0 8px; font-size: 15px; }}
    .strategy p {{ margin: 0; color: var(--muted); line-height: 1.65; }}
    .muted {{ color: var(--muted); }}
    .footnote {{ color: var(--muted); font-size: 12px; line-height: 1.7; }}
    @media (max-width: 920px) {{
      .grid, .strategy {{ grid-template-columns: repeat(2, minmax(0, 1fr)); }}
      table {{ min-width: 760px; }}
      .table-wrap {{ overflow-x: auto; }}
    }}
    @media (max-width: 560px) {{
      .grid, .strategy {{ grid-template-columns: 1fr; }}
      h1 {{ font-size: 25px; }}
      main, .hero {{ padding-left: 14px; padding-right: 14px; }}
    }}
  </style>
</head>
<body>
  <header>
    <div class="hero">
      <p class="eyebrow">AI TestOps Quality Dashboard</p>
      <h1>{html.escape(report.project)} 代码覆盖率看板</h1>
      <p>基于 JaCoCo XML 自动生成，优先暴露必须补测的业务逻辑、风险分析、覆盖匹配和门禁计算代码；实体、DTO、VO、配置类单独归为低优先级，避免为了数字补无效测试。</p>
    </div>
  </header>
  <main>
    <div class="grid">
      <article class="card kpi"><span>行覆盖率</span><strong class="metric {status_for(report.line.ratio, line_min)}">{percent(report.line.ratio)}</strong><small>目标 >= {line_min * 100:.0f}%</small></article>
      <article class="card kpi"><span>分支覆盖率</span><strong class="metric {status_for(report.branch.ratio, branch_min)}">{percent(report.branch.ratio)}</strong><small>目标 >= {branch_min * 100:.0f}%</small></article>
      <article class="card kpi"><span>方法覆盖率</span><strong>{percent(report.method.ratio)}</strong><small>用于辅助判断覆盖广度</small></article>
      <article class="card kpi"><span>必须覆盖缺口</span><strong class="metric {status_for(1 - min(1, len(must_gaps) / max(1, must_count)), 1)}">{len(must_gaps)}</strong><small>必须覆盖类总数 {must_count}，低优先级类 {exempt_count}</small></article>
    </div>

    <section class="card">
      <h2>必须优先补测</h2>
      <p class="section-lead">这些类包含接口契约、业务规则、风险识别、覆盖匹配或上线门禁逻辑，行覆盖率低于目标时应优先补测试。</p>
      <div class="table-wrap">
        <table>
          <thead><tr><th>类</th><th>优先级</th><th>行覆盖率</th><th>分支覆盖率</th><th>未覆盖行</th><th>原因</th></tr></thead>
          <tbody>{class_rows(must_gaps, line_min)}</tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <h2>低覆盖类 Top 20</h2>
      <p class="section-lead">这份列表用于发现整体覆盖率短板；先处理“必须覆盖”，再处理普通实现类。</p>
      <div class="table-wrap">
        <table>
          <thead><tr><th>类</th><th>优先级</th><th>行覆盖率</th><th>分支覆盖率</th><th>未覆盖行</th><th>建议</th></tr></thead>
          <tbody>{class_rows(low_classes, line_min)}</tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <h2>包维度覆盖率</h2>
      <div class="table-wrap">
        <table>
          <thead><tr><th>包</th><th>类数量</th><th>行覆盖率</th><th>分支覆盖率</th><th>未覆盖行</th></tr></thead>
          <tbody>{package_table_rows(report.classes, line_min)}</tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <h2>可降低优先级</h2>
      <p class="section-lead">这些代码可以通过上层测试间接覆盖，通常不需要为了覆盖率单独写脆弱测试。</p>
      <div class="table-wrap">
        <table>
          <thead><tr><th>类</th><th>优先级</th><th>行覆盖率</th><th>分支覆盖率</th><th>未覆盖行</th><th>原因</th></tr></thead>
          <tbody>{class_rows(exempt_classes, line_min)}</tbody>
        </table>
      </div>
    </section>

    <section class="strategy">
      <div><h3>必须覆盖</h3><p>Controller、Service、Diff 风险规则、覆盖匹配、门禁计算、JSON/文件/TraceId 等会改变业务结果的工具类。</p></div>
      <div><h3>适合间接覆盖</h3><p>Mapper、配置装配、外部客户端可通过集成测试或边界 mock 覆盖关键路径，不追逐每一行样板代码。</p></div>
      <div><h3>无需强追</h3><p>Entity、DTO、VO、枚举、启动类、纯静态资源和构建产物不作为覆盖率提升重点。</p></div>
    </section>

    <p class="footnote">报告来源：<code>{html.escape(report.source_xml)}</code>。覆盖率是质量信号，不是唯一目标；低风险样板代码排除后，门禁会更贴近真实业务风险。</p>
  </main>
</body>
</html>
""",
        encoding="utf-8",
    )
    return html_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate an HTML coverage dashboard from JaCoCo XML")
    parser.add_argument("--project", default="ai-testops", help="Project name shown in the dashboard")
    parser.add_argument("--jacoco-xml", required=True, help="Path to JaCoCo XML report")
    parser.add_argument("--output-dir", required=True, help="Directory where index.html will be written")
    parser.add_argument("--line-min", type=float, default=0.50, help="Line coverage target")
    parser.add_argument("--branch-min", type=float, default=0.30, help="Branch coverage target")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not Path(args.jacoco_xml).exists():
        write_missing_dashboard(args.project, args.jacoco_xml, args.output_dir)
        return 0
    report = parse_jacoco_xml(args.jacoco_xml, args.project)
    generate_dashboard(report, args.output_dir, args.line_min, args.branch_min)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
