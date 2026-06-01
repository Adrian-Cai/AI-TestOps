**Mermaid 格式流程图**，内容按照 Demo 的实现路径来设计，适合后面一步一步落地。

总流程：


---

## Mermaid 总流程图

```mermaid
flowchart TD

    A[用户输入需求片段<br/>或上传文件] --> B{输入方式}

    B -->|文本输入| B1[直接保存原始需求文本]
    B -->|文件上传| B2[上传文件]

    B2 --> C[文件接入与预处理<br/>格式识别 / 大小校验 / 任务创建]
    C --> D[Apache Tika 解析文件]
    D --> E[提取文本与元数据<br/>正文 / 标题 / 页码或工作表 / 作者 / 创建时间 / 文件类型]
    E --> F[内容清洗与结构化<br/>去噪 / 分段 / 分章节 / 分块]
    B1 --> F

    F --> G[生成统一文档结构]

    G --> G1[原始文件存储<br/>对象存储或本地文件]
    G --> G2[文档主表存储<br/>document_id / file_name / file_type / status]
    G --> G3[解析结果存储<br/>raw_text / metadata / sections / chunks]
    G --> G4[向量索引 可选<br/>Milvus / PGVector / Elasticsearch]

    G --> H[AI 需求解析]
    H --> H1[提取需求点]
    H --> H2[提取业务规则]
    H --> H3[提取接口或字段约束]
    H --> H4[提取异常场景]
    H --> H5[提取风险点]

    H1 --> I[组装结构化需求对象]
    H2 --> I
    H3 --> I
    H4 --> I
    H5 --> I

    I --> J[Prompt 模板编排]
    J --> K[调用大模型 API<br/>OpenAI / 国产大模型]
    K --> L[按照 JSON Schema 输出测试用例]

    L --> M[生成结构化测试用例]
    M --> M1[标题]
    M --> M2[前置条件]
    M --> M3[测试步骤]
    M --> M4[预期结果]
    M --> M5[优先级]
    M --> M6[关联需求]
    M --> M7[风险标签]

    M --> N[结果校验与规则检查]
    N --> N1[Schema 校验]
    N --> N2[必填字段校验]
    N --> N3[重复用例检测]
    N --> N4[格式规范化]

    N --> O[生成记录与版本落库<br/>保存 prompt / 模型版本 / 输入快照 / 输出结果 / 校验状态]
    O --> P[人工编辑与确认]

    P --> Q{是否通过人工确认}

    Q -->|否| R[回流重新生成<br/>调整需求解析结果或 Prompt]
    R --> J

    Q -->|是| S[确认入库]
    S --> T[测试用例库 / 导出结果]
    T --> T1[用例列表]
    T --> T2[需求-用例映射]
    T --> T3[导出 Excel / JSON]
```

---

## 分阶段实现版 Mermaid

```mermaid
flowchart TD

    A[第一步<br/>用户输入需求片段或上传文件]
    A --> B[第二步<br/>统一接入层处理输入]
    B --> C[第三步<br/>Apache Tika 解析文件内容]
    C --> D[第四步<br/>提取文本与元数据]
    D --> E[第五步<br/>内容清洗与结构化]
    E --> F[第六步<br/>保存解析结果]

    F --> F1[保存原始文件]
    F --> F2[保存文档主表]
    F --> F3[保存解析结果 JSON]
    F --> F4[可选 保存向量索引]

    F --> G[第七步<br/>AI 提取需求信息]
    G --> G1[需求点]
    G --> G2[业务规则]
    G --> G3[字段约束]
    G --> G4[异常场景]
    G --> G5[风险点]

    G1 --> H[第八步<br/>组装结构化需求]
    G2 --> H
    G3 --> H
    G4 --> H
    G5 --> H

    H --> I[第九步<br/>Prompt 模板编排]
    I --> J[第十步<br/>调用大模型生成测试用例]
    J --> K[第十一步<br/>输出结构化 TestCase JSON]

    K --> L[第十二步<br/>校验生成结果]
    L --> L1[Schema 校验]
    L --> L2[字段完整性校验]
    L --> L3[重复检测]
    L --> L4[格式规范化]

    L --> M[第十三步<br/>记录生成过程与版本]
    M --> N[第十四步<br/>人工查看与编辑]
    N --> O{是否确认通过}

    O -->|否| P[重新生成]
    P --> I

    O -->|是| Q[最终入库]
    Q --> R[输出测试用例列表或导出文件]
```

---

## 我建议你配套使用的结构化对象

因为是一步一步实现，所以 Mermaid 图之外，同步固定 3 个核心对象。

### 1. 文档解析结果对象

```json
{
  "document_id": "doc_001",
  "file_name": "需求说明.pdf",
  "file_type": "pdf",
  "raw_text": "原始提取文本",
  "metadata": {
    "author": "xxx",
    "created_at": "2026-04-30",
    "page_count": 10
  },
  "sections": [
    {
      "title": "模块A",
      "content": "章节内容"
    }
  ],
  "chunks": [
    {
      "chunk_id": "chunk_001",
      "text": "分块内容"
    }
  ]
}
```

### 2. 需求提取结果对象

```json
{
  "requirements": [
    "用户可以提交订单"
  ],
  "business_rules": [
    "订单金额必须大于0"
  ],
  "api_list": [
    "/api/order/create"
  ],
  "field_constraints": [
    "手机号必须11位"
  ],
  "exception_cases": [
    "库存不足时提交失败"
  ],
  "risks": [
    "重复提交订单"
  ]
}
```

### 3. 测试用例对象

```json
{
  "case_id": "TC_001",
  "title": "正常提交订单",
  "preconditions": [
    "用户已登录",
    "商品库存充足"
  ],
  "steps": [
    "进入下单页面",
    "填写订单信息",
    "点击提交"
  ],
  "expected_result": "订单提交成功",
  "priority": "P1",
  "requirement_refs": [
    "REQ_001"
  ],
  "risk_tags": [
    "核心链路"
  ]
}
```

---

## 如果是按开发顺序做，这样拆任务

### 第一阶段

先做：

* 输入需求文本
* 上传文件
* Tika 解析
* 解析结果存储

### 第二阶段

再做：

* AI 提取需求点
* 输出结构化需求 JSON

### 第三阶段

再做：

* Prompt 模板
* 调用模型
* 生成测试用例 JSON

### 第四阶段

最后做：

* 校验
* 生成记录落库
* 人工编辑确认
* 导出结果

