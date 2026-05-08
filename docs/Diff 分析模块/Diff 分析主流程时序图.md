：**Diff 分析不应该孤立成一个“单独功能”，它应该是你整个 AI 测试平台里的一个下游质量分析服务**。

更准确地说，应该从“单点 AI 生成用例”升级成：

> **需求阶段生成测试资产，代码阶段校验测试覆盖，合并阶段形成质量准入。**

所以它不是一个服务自己干完，而是多个服务串起来。

---

# 一、整体服务关系应该这样理解

现在已经有了：

```text
需求文档
  ↓
AI 生成测试用例服务
  ↓
测试用例落库
```

接下来要加的是：

```text
代码 Diff
  ↓
Diff 风险分析服务
  ↓
查询已生成测试用例
  ↓
判断代码变更是否被测试用例覆盖
  ↓
生成风险分析报告
  ↓
未覆盖则补充用例
  ↓
执行验证
  ↓
合并准入
```

所以核心关系是：

```text
AI 用例生成服务  →  测试用例库  →  Diff 风险分析服务
```

Diff 分析服务依赖的不是“重新生成用例”，而是依赖 **AI 用例生成服务沉淀下来的测试资产**。

---

# 二、推荐的服务拆分

把系统拆成这几个服务。

```text
1. 需求管理服务
2. 文档解析服务
3. AI 用例生成服务
4. 测试用例管理服务
5. Git / Diff 接入服务
6. Diff 风险分析服务
7. 覆盖率匹配服务
8. 补充用例生成服务
9. 测试执行回流服务
10. 合并准入报告服务
```

不过第一版不用真的拆成 10 个独立微服务，可以先在一个后端项目里按模块拆清楚。等业务复杂后，再把重点模块独立出去。

---

# 三、每个服务负责什么？

## 1. 需求管理服务

负责管理需求本身。

它保存：

```text
需求ID
需求标题
需求描述
所属项目
所属模块
需求状态
关联文档
关联分支
```

这是整个链路的起点。

---

## 2. 文档解析服务

负责把需求文档解析成结构化内容。

输入：

```text
Word / PDF / Markdown / TXT / 接口文档
```

输出：

```json
{
  "requirement_points": [],
  "business_rules": [],
  "api_list": [],
  "fields": [],
  "exception_scenarios": [],
  "risk_points": []
}
```

---

## 3. AI 用例生成服务

这是你已经做完的核心能力。

它负责：

```text
根据需求文档生成测试用例
```

输出后落库：

```text
测试用例表
测试步骤表
用例与需求关系表
用例标签表
生成记录表
```

这个服务的结果会成为 Diff 分析服务的输入之一。

---

## 4. 测试用例管理服务

这个服务很关键，它不是 AI 服务，但它负责提供“已有用例资产”。

Diff 分析服务后面要通过它查询：

```text
某个需求下有哪些用例？
某个模块下有哪些历史用例？
某个接口有哪些用例？
某个字段有哪些用例？
某类风险有没有已有用例覆盖？
```

所以它需要提供接口，例如：

```http
GET /api/test-cases?requirementId=10001
GET /api/test-cases?moduleId=order
GET /api/test-cases?relatedApi=/api/order/create
GET /api/test-cases?riskTag=幂等
```

---

## 5. Git / Diff 接入服务

负责获取代码变更。

输入：

```text
仓库地址
源分支
目标分支
PR / MR ID
commit hash
```

输出：

```json
{
  "changed_files": [],
  "commits": [],
  "patch": "",
  "diff_summary": ""
}
```

它只负责拿代码变更，不负责判断业务风险。

---

## 6. Diff 风险分析服务

这是你接下来要做的核心服务。

它负责：

```text
根据代码 Diff 分析影响范围和风险点
```

输入包括：

```text
代码 Diff
需求背景
变更文件
变更方法
历史缺陷
业务规则
```

输出：

```text
风险项
影响场景
风险等级
建议测试点
```

注意：这个服务不直接判断“有没有用例覆盖”，它只先把风险识别出来。

---

## 7. 覆盖率匹配服务

这个服务负责把：

```text
Diff 风险项
```

和：

```text
已有测试用例
```

进行匹配。

它判断：

```text
这个风险有没有被已有测试用例覆盖？
是完全覆盖、部分覆盖，还是未覆盖？
```

输出：

```json
{
  "risk_id": 1001,
  "coverage_status": "NOT_COVERED",
  "matched_cases": [],
  "missing_scenarios": [
    "等级等于3的边界场景未覆盖"
  ]
}
```

这一步是工具最有价值的地方。

---

## 8. 补充用例生成服务

只有当风险未覆盖或部分覆盖时，才调用它。

它输入：

```text
风险项
缺失场景
代码变更说明
已有用例
```

输出：

```text
补充测试用例
```

这些补充用例也要落到测试用例库里，并标记来源：

```text
source_type = DIFF_SUPPLEMENT
source_id = risk_id
```

这样以后就知道：

```text
哪些用例是需求阶段生成的？
哪些用例是代码 Diff 阶段补充的？
哪些用例是人工新增的？
```

---

## 9. 测试执行回流服务

负责接收测试结果。

比如：

```text
用例执行通过
用例执行失败
用例阻塞
创建缺陷
关联缺陷单
```

这一步让风险闭环起来。

---

## 10. 合并准入报告服务

最后根据风险状态生成结论：

```text
PASS：允许合并
WARNING：可以合并但建议关注
BLOCK：不建议合并
MANUAL_REVIEW：需要人工评审
```

---

# 四、推荐的完整主流程

现在的完整链路应该是这样：

```mermaid
flowchart TD
    A[上传/输入需求文档] --> B[文档解析服务]
    B --> C[AI用例生成服务]
    C --> D[测试用例管理服务]
    D --> E[(测试用例数据库)]

    F[开发完成并提交分支/PR] --> G[Git Diff接入服务]
    G --> H[Diff风险分析服务]

    E --> I[覆盖率匹配服务]
    H --> I

    I --> J{是否覆盖风险}

    J -->|已覆盖| K[标记风险已覆盖]
    J -->|部分覆盖| L[生成补充测试建议]
    J -->|未覆盖| M[补充用例生成服务]

    L --> N[人工确认]
    M --> O[补充用例落库]
    O --> E

    K --> P[合并准入报告服务]
    N --> P
    O --> Q[测试执行回流服务]
    Q --> P

    P --> R{准入结论}
    R -->|PASS| S[允许合并]
    R -->|WARNING| T[可合并但需关注]
    R -->|BLOCK| U[阻塞合并]
    R -->|MANUAL_REVIEW| V[人工评审]
```

---

# 五、服务调用时序图

这版更符合“多个服务串起来”。

```mermaid
sequenceDiagram
    autonumber

    actor User as 用户
    participant Req as 需求管理服务
    participant Doc as 文档解析服务
    participant Gen as AI用例生成服务
    participant Case as 测试用例管理服务
    participant Git as Git/Diff接入服务
    participant Risk as Diff风险分析服务
    participant Cover as 覆盖率匹配服务
    participant Supp as 补充用例生成服务
    participant Exec as 测试执行回流服务
    participant Gate as 合并准入报告服务
    participant DB as MariaDB

    User->>Req: 创建需求并上传需求文档
    Req->>DB: 保存需求信息

    Req->>Doc: 解析需求文档
    Doc-->>Req: 返回结构化需求内容

    Req->>Gen: 请求生成测试用例
    Gen->>Gen: 调用大模型生成用例
    Gen-->>Req: 返回测试用例结果

    Req->>Case: 保存生成的测试用例
    Case->>DB: 测试用例落库<br/>source_type=REQUIREMENT_GENERATED

    User->>Git: 输入仓库、源分支、目标分支或PR信息
    Git->>Git: 获取代码Diff
    Git-->>Risk: 返回变更文件、Patch、Commit信息

    Risk->>Req: 查询关联需求信息
    Req-->>Risk: 返回需求背景、业务规则

    Risk->>Risk: 规则引擎识别风险
    Risk->>Risk: 大模型分析影响场景
    Risk->>DB: 保存风险项

    Risk->>Case: 根据需求ID/模块/接口查询已有测试用例
    Case->>DB: 查询需求阶段已生成用例
    Case-->>Risk: 返回已有测试用例列表

    Risk->>Cover: 请求判断风险覆盖情况
    Cover->>Cover: 匹配风险项与已有用例
    Cover-->>Risk: 返回覆盖结果

    Risk->>DB: 保存风险与用例关联关系

    alt 风险已覆盖
        Risk->>Gate: 请求生成准入结论
    else 风险部分覆盖
        Risk->>Supp: 生成补充测试建议/补充用例
        Supp->>Case: 保存补充用例
        Case->>DB: 补充用例落库<br/>source_type=DIFF_SUPPLEMENT
        Supp-->>Risk: 返回补充用例ID
        Risk->>DB: 绑定风险与补充用例
    else 风险未覆盖
        Risk->>Supp: 根据风险生成补充测试用例
        Supp->>Case: 保存补充用例
        Case->>DB: 补充用例落库<br/>source_type=DIFF_SUPPLEMENT
        Supp-->>Risk: 返回补充用例ID
        Risk->>DB: 更新风险状态<br/>WAIT_TEST
    end

    User->>Exec: 执行测试并回填结果
    Exec->>DB: 保存测试执行结果

    Exec->>Risk: 回传风险验证结果
    Risk->>DB: 更新风险状态<br/>PASSED/FAILED/BLOCKED

    Risk->>Gate: 重新计算合并准入结论
    Gate->>DB: 保存准入报告
    Gate-->>User: 返回 PASS/WARNING/BLOCK/MANUAL_REVIEW
```

---

# 六、系统的依赖关系应该这样设计

不是 Diff 服务直接依赖 AI 生成用例服务，而是：

```text
Diff 风险分析服务
    ↓
依赖测试用例管理服务
    ↓
测试用例管理服务的数据来自 AI 用例生成服务
```

也就是说，推荐依赖关系是：

```text
AI 用例生成服务 → 测试用例管理服务 ← Diff 风险分析服务
```

不要设计成：

```text
Diff 风险分析服务 → AI 用例生成服务
```

为什么？

因为 Diff 分析的时候，需要的是“已经生成并确认过的测试用例”，不是重新调用 AI 生成用例。

正确依赖应该是：

```text
Diff 分析服务依赖测试用例资产，而不是直接依赖需求生成流程。
```

只有在发现未覆盖风险时，才需要调用“补充用例生成能力”。

---

# 七、服务边界建议

这样划分边界：

| 服务            |          是否独立 | 职责                       |
| ------------- | ------------: | ------------------------ |
| 需求管理服务        |       可以先作为模块 | 管理需求、关联文档、关联分支           |
| 文档解析服务        |           可独立 | 解析 PDF/Word/Markdown/TXT |
| AI 用例生成服务     |          建议独立 | 根据需求生成测试用例               |
| 测试用例管理服务      |     建议独立或核心模块 | 用例查询、编辑、确认、版本管理          |
| Git/Diff 接入服务 |        建议独立模块 | 获取分支 Diff、PR Diff        |
| Diff 风险分析服务   |          建议独立 | 分析代码变更风险                 |
| 覆盖率匹配服务       |  可作为 Diff 子模块 | 判断风险是否被用例覆盖              |
| 补充用例生成服务      | 可复用 AI 用例生成能力 | 对未覆盖风险生成用例               |
| 测试执行服务        |          后续独立 | 用例执行、结果回流                |
| 准入报告服务        |  可作为 Diff 子模块 | 生成合并准入报告                 |

第一版不要微服务化太重，采用：

```text
一个后端项目
多个业务模块
服务边界先设计清楚
```

例如：

```text
ai-testops-backend
 ├── requirement
 ├── document-parser
 ├── testcase
 ├── ai-generation
 ├── git-diff
 ├── risk-analysis
 ├── coverage
 ├── execution
 └── merge-gate
```

等后面业务复杂后，再拆成独立服务。

---

# 八、关键数据流

要特别关注这个数据链路：

```text
requirement_id
    ↓
generated_case_id
    ↓
diff_task_id
    ↓
risk_id
    ↓
risk_case_relation
    ↓
execution_result
    ↓
merge_gate_report
```

也就是：

```text
需求 → 用例 → 代码风险 → 覆盖关系 → 执行结果 → 准入结论
```

这个链路打通，平台才真正有价值。

---

# 九、最重要的数据库关系

现在已有测试用例表的话，后面最重要的是建立这几种关系。

## 1. 需求和用例关系

```text
requirement_id → case_id
```

表示这些用例是根据某个需求生成的。

---

## 2. Diff 任务和需求关系

```text
diff_task_id → requirement_id
```

表示这次代码变更对应哪个需求。

---

## 3. 风险和用例关系

```text
risk_id → case_id
```

表示这个风险被哪些用例覆盖。

---

## 4. 风险和执行结果关系

```text
risk_id → execution_result_id
```

表示这个风险最终是否验证通过。

---

## 5. Diff 任务和准入报告关系

```text
diff_task_id → merge_gate_report_id
```

表示这次分支是否可以合并。

---

# 十、这里建议加一个“质量资产中心”

为了让服务串得更自然，建议你在产品概念里加一个中间层：

## 质量资产中心

它沉淀这些东西：

```text
需求结构化结果
测试用例
业务规则
风险标签
历史缺陷
接口信息
字段信息
执行结果
```

然后：

```text
AI 用例生成服务
```

负责往质量资产中心写入用例。

```text
Diff 分析服务
```

负责从质量资产中心读取用例、规则、历史缺陷，再生成风险分析。

这样架构就更清晰。

可以理解为：

```text
需求阶段：生产质量资产
代码阶段：消费质量资产并校验覆盖
执行阶段：回流质量资产并持续优化
```

---

# 十一、最终推荐架构图

```mermaid
flowchart LR
    subgraph Input[输入层]
        A1[需求文档]
        A2[代码分支/PR]
        A3[历史缺陷]
        A4[接口文档]
    end

    subgraph Service[服务层]
        B1[文档解析服务]
        B2[AI用例生成服务]
        B3[测试用例管理服务]
        B4[Git/Diff接入服务]
        B5[Diff风险分析服务]
        B6[覆盖率匹配服务]
        B7[补充用例生成服务]
        B8[测试执行回流服务]
        B9[合并准入报告服务]
    end

    subgraph Asset[质量资产中心]
        C1[(需求结构化结果)]
        C2[(测试用例库)]
        C3[(风险项库)]
        C4[(覆盖关系库)]
        C5[(执行结果库)]
        C6[(历史缺陷库)]
        C7[(准入报告库)]
    end

    A1 --> B1
    B1 --> C1
    C1 --> B2
    B2 --> B3
    B3 --> C2

    A2 --> B4
    B4 --> B5
    C1 --> B5
    C6 --> B5
    B5 --> C3

    C2 --> B6
    C3 --> B6
    B6 --> C4

    C4 --> B7
    B7 --> B3
    B3 --> C2

    C2 --> B8
    C3 --> B8
    B8 --> C5

    C3 --> B9
    C4 --> B9
    C5 --> B9
    B9 --> C7
```

---

# 十二、现在这句话可以作为产品定位

把这个能力定义成：

> 在需求阶段，平台通过 AI 生成并沉淀测试用例；在代码提交阶段，平台基于分支 Diff 识别代码变更风险，并与已生成测试用例进行覆盖匹配，自动发现测试遗漏场景，生成补充用例和合并准入报告，形成从需求到代码再到测试验证的质量闭环。

这句话非常重要，因为它把两个能力串起来了：

```text
AI 生成用例
+
代码 Diff 风险分析
+
测试覆盖校验
+
合并准入
```

这就不是两个孤立功能，而是一套完整的 AI TestOps 平台能力
