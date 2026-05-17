# JMeter 压测使用指南

## 目录结构

```
jmeter/
├── ai-testops-main-flow.jmx    # JMeter测试计划
├── 压测接口文档.md              # 接口详细文档
├── README.md                    # 本文件
└── data/
    ├── upload-iterations.csv    # 上传流程测试数据
    └── draft-groups.csv         # 审批流程测试数据
```

## 快速开始

### 1. 环境准备

- 安装 JMeter 5.6+
- 确保服务已启动: `http://localhost:8080`

### 2. 准备测试数据

**上传文件：**
将测试文件复制到 JMeter 的 `bin` 目录或修改 `UPLOAD_FILE` 变量路径：

```bash
# 从项目根目录复制测试文件
cp scripts/perf/fixtures/需求文档01_智能订单履约与售后协同系统.md apache-jmeter-5.6/bin/
```

**CSV 数据文件：**
`data/` 目录下已有示例数据，根据实际情况修改。

### 3. 运行压测

**GUI 模式（调试用）：**
```bash
jmeter
# 打开 ai-testops-main-flow.jmx
```

**命令行模式（正式压测）：**
```bash
jmeter -n -t ai-testops-main-flow.jmx -l results/test-result.jtl -e -o results/report
```

### 4. 查看结果

```bash
# 生成HTML报告
jmeter -g results/test-result.jtl -o results/html-report

# 打开报告
start results/html-report/index.html
```

## 配置说明

### 线程组参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 线程数 | 10 | 并发用户数 |
| Ramp-Up | 5秒 | 启动时间 |
| 持续时间 | 300秒 | 压测时长 |
| 循环次数 | -1 | 无限循环 |

### 用户定义变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| BASE_URL | http://localhost:8080 | 服务地址 |
| THINK_TIME | 1000 | 思考时间(ms) |
| UPLOAD_FILE | ./fixtures/需求文档01.md | 上传文件路径 |

## 压测场景

### 场景一: 上传解析流程

1. 上传文档 → 获取 documentId
2. 使用 documentId 解析文档
3. 思考时间 1秒

### 场景二: 审批导出流程

1. 批量审批草稿用例
2. 导出JSON格式
3. 导出Excel格式
4. 思考时间 1秒

> 注意: 两个场景默认互斥，运行时启用其中一个

## 性能指标

| 指标 | 阈值 |
|------|------|
| 请求失败率 | < 1% |
| 响应时间 P95 | < 2500ms |
| 响应时间 P99 | < 5000ms |
| 业务成功率 | > 99% |

## 常见问题

### Q: 如何调整压测强度？

修改线程组的 `ThreadGroup.num_threads` 值：
- 低负载: 5-10 线程
- 中负载: 20-50 线程
- 高负载: 100+ 线程

### Q: 如何只运行某个场景？

在 JMeter GUI 中：
1. 右键点击要禁用的线程组
2. 选择 "禁用"

或在命令行使用 `-J` 参数：
```bash
jmeter -n -t ai-testops-main-flow.jmx -Jthreads=20 -Jduration=600
```

### Q: 如何查看实时结果？

添加监听器：
- 聚合报告 (Aggregate Report)
- 响应时间图 (Response Time Graph)
- 活跃线程数 (Active Threads Over Time)
