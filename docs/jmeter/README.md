# AI-TestOps JMeter 压测使用指南

本目录用于压测 AI-TestOps 主流程中不依赖大模型的部分：

```text
文件上传 -> 文档解析 -> 人工确认草稿 -> 保存正式用例 -> JSON/Excel 导出
```

`提取结构化需求` 和 `生成测试用例草稿` 依赖大模型，不纳入本 JMeter 压测计时。确认与导出场景需要提前准备草稿数据。

## 目录结构

```text
jmeter/
├── ai-testops-main-flow.jmx       # 主流程 JMeter 测试计划
├── 压测接口文档.md                 # 接口、造数、指标与计划
├── README.md
├── data/
│   ├── upload-files.csv
│   └── draft-groups.csv
└── results/
```

## 运行方式

正式压测请使用 JMeter 非 GUI 模式。JMeter 官方推荐使用 `-n -t ... -l ... -e -o ...` 运行并生成 HTML 报告。

如果 `jmeter` 命令不在 PATH，请改用完整路径，例如：

```powershell
& 'D:\tools\apache-jmeter-5.6.3\bin\jmeter.bat' -n -t ai-testops-main-flow.jmx
```

先进入 JMeter 文件目录：

```powershell
cd D:\AllProject\AI-TestOps\docs\jmeter
```

当前唯一维护的 JMeter 计划是 `docs/jmeter/ai-testops-main-flow.jmx`。旧实验版 `HTTP请求.jmx` 不再维护，避免重复 sampler 造成流量失真。

### 1. Smoke：上传 + 解析

```powershell
jmeter -n `
  -t ai-testops-main-flow.jmx `
  -Jtarget.protocol=https `
  -Jtarget.host=ai-case.wiac.xyz `
  -Jflow=upload_parse `
  -Jthreads=1 `
  -Jramp.seconds=5 `
  -Jduration.seconds=30 `
  -Jthink.time.ms=1000 `
  -Jupload.files=data/upload-files.csv `
  -l results/upload-parse-smoke.jtl `
  -e -o results/upload-parse-smoke-report
```

### 2. 基线：上传 + 解析

```powershell
jmeter -n `
  -t ai-testops-main-flow.jmx `
  -Jtarget.protocol=https `
  -Jtarget.host=ai-case.wiac.xyz `
  -Jflow=upload_parse `
  -Jthreads=2 `
  -Jramp.seconds=30 `
  -Jduration.seconds=300 `
  -Jthink.time.ms=1000 `
  -Jupload.files=data/upload-files.csv `
  -l results/upload-parse-baseline.jtl `
  -e -o results/upload-parse-baseline-report
```

### 3. 上传文件轮询数据

上传场景通过 CSV 轮询文件，格式固定为：

```csv
filePath,fileName,mimeType
D:\DOCS\file-1.docx,file-1.docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document
D:\DOCS\file-2.txt,file-2.txt,text/plain
D:\DOCS\file-3.md,file-3.md,text/markdown
```

推荐三组复压数据：

- 中文单文件：CSV 只保留中文文件一行，用于验证中文文件名链路。
- 英文单文件：CSV 只保留英文文件一行，用于排除中文文件名影响。
- 多文件轮询：CSV 放多行不同内容文件，用于验证并发创建 document 和解析稳定性。

### 4. 批量确认 + 导出

先把 `data/draft-groups.csv` 替换为真实待确认草稿数据，不能重复消费同一批 `draftCaseIds`。

```powershell
jmeter -n `
  -t ai-testops-main-flow.jmx `
  -Jtarget.protocol=https `
  -Jtarget.host=ai-case.wiac.xyz `
  -Jflow=approve_export `
  -Jthreads=3 `
  -Jramp.seconds=30 `
  -Jduration.seconds=300 `
  -Jexport.json=true `
  -Jexport.excel=true `
  -l results/approve-export-baseline.jtl `
  -e -o results/approve-export-baseline-report
```

只测 JSON 导出：

```powershell
jmeter -n `
  -t ai-testops-main-flow.jmx `
  -Jtarget.protocol=https `
  -Jtarget.host=ai-case.wiac.xyz `
  -Jflow=approve_export `
  -Jthreads=5 `
  -Jduration.seconds=300 `
  -Jexport.json=true `
  -Jexport.excel=false `
  -l results/approve-json.jtl `
  -e -o results/approve-json-report
```

## 常用参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `target.protocol` | `https` | 目标协议 |
| `target.host` | `ai-case.wiac.xyz` | 目标域名，不要带 `https://` |
| `target.port` | 空 | 目标端口，HTTPS 默认 443 可留空 |
| `flow` | `upload_parse` | `upload_parse` 或 `approve_export` |
| `threads` | `2` | 并发线程数 |
| `ramp.seconds` | `10` | 线程启动爬坡时间 |
| `duration.seconds` | `60` | 压测持续时间 |
| `think.time.ms` | `1000` | 每轮业务流程后的等待时间 |
| `upload.files` | `data/upload-files.csv` | 上传文件轮询 CSV，列为 `filePath,fileName,mimeType` |
| `export.json` | `true` | 是否导出 JSON |
| `export.excel` | `true` | 是否导出 Excel |

## 结果查看

HTML 报告生成后打开：

```powershell
start results/upload-parse-baseline-report/index.html
```

重点看：

- `Throughput`：吞吐量，近似 QPS/TPS。
- `Average`：平均响应时间。
- `90% Line / 95% Line / 99% Line`：分位响应时间。
- `Error %`：错误率。
- 每个 sampler 和 `TX-*` Transaction Controller 的耗时。

## 注意事项

- `approve_export` 必须使用真实 `PENDING` 草稿数据；示例 CSV 只展示格式。
- `data/draft-groups.csv` 的 `draftCaseIds` 是 JSON 数组字符串，CSV 已开启 quoted data。
- Excel 导出会消耗更多 CPU 和内存，也更容易受 6 Mbps 公网带宽限制。
- 正式压测不要打开 GUI 监听器；GUI 只用于调试测试计划。
