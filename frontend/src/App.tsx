import {
  Alert,
  AutoComplete,
  Badge,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Layout,
  List,
  Menu,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Steps,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
  message
} from "antd";
import type { MenuProps, UploadProps } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  ApiOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudUploadOutlined,
  CodeOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  EditOutlined,
  ExperimentOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  RobotOutlined,
  SaveOutlined
} from "@ant-design/icons";
import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api";
import {
  CASE_TYPE_OPTIONS,
  normalizeArray,
  jsonArrayRule,
  getDefaultCaseTypeForRiskLevel,
  formatCaseType,
  toEnglishCaseType
} from "./utils";
import type {
  DocumentChunkVO,
  DocumentParseSummaryVO,
  DocumentVO,
  DiffAnalysisReportVO,
  DiffAnalysisSourceVO,
  DiffSupplementCaseVO,
  DiffAnalysisTaskVO,
  DiffChangedFileVO,
  DiffRiskItemVO,
  GenerationRecordVO,
  OperationLog,
  RequirementExtractVO,
  TestCaseDraftVO,
  TestCaseGenerateVO,
  TestCaseVO,
  ValidationResultVO
} from "./types";

const { Header, Sider, Content } = Layout;
const { Text, Title, Paragraph } = Typography;
const { TextArea } = Input;
const Dragger = Upload.Dragger;

const allowedExtensions = ["md", "txt", "doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx"];
const maxFileSize = 50 * 1024 * 1024;
const exampleRequirement = `用户可以提交订单。

业务规则：
1. 用户登录后才能提交订单。
2. 商品库存不足时提交失败，并提示库存不足。
3. 订单金额必须大于 0。
4. 同一订单号不能重复提交。
5. 订单提交成功后状态变为待支付。`;

type StepKey = "input" | "parse" | "generate" | "review" | "export" | "diff" | "diff-detail";
type StepStatus = "wait" | "process" | "finish" | "error";

const stepKeys: StepKey[] = ["input", "parse", "generate", "review", "export"];
const diffStepKeys: StepKey[] = ["diff", "diff-detail"];

const menuItems: MenuProps["items"] = [
  { key: "input", icon: <CloudUploadOutlined />, label: "输入材料" },
  { key: "parse", icon: <FileSearchOutlined />, label: "文档解析" },
  { key: "generate", icon: <RobotOutlined />, label: "AI 生成" },
  { key: "review", icon: <EditOutlined />, label: "人工确认" },
  { key: "export", icon: <DownloadOutlined />, label: "保存导出" },
  {
    key: "diff-menu",
    icon: <CodeOutlined />,
    label: "Diff 分析",
    children: [
      { key: "diff", label: "任务与准入报告" },
      { key: "diff-detail", label: "变更文件和风险覆盖分析" }
    ]
  }
];

function App() {
  const [messageApi, contextHolder] = message.useMessage();
  const [currentStep, setCurrentStep] = useState<StepKey>("input");
  const [busy, setBusy] = useState<string | null>(null);
  const [progressPercent, setProgressPercent] = useState(0);
  const [progressLabel, setProgressLabel] = useState("");
  const [progressVisible, setProgressVisible] = useState(false);
  const progressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const progressDoneRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const longOps = ["extract", "generate"];
    if (busy && longOps.includes(busy)) {
      setProgressVisible(true);
      setProgressPercent(0);
      setProgressLabel(busy === "extract" ? "正在提取结构化需求..." : "正在生成测试用例草稿...");
      if (progressDoneRef.current) clearTimeout(progressDoneRef.current);
      const start = Date.now();
      progressTimerRef.current = setInterval(() => {
        const elapsed = (Date.now() - start) / 1000;
        let pct: number;
        if (elapsed < 2) {
          pct = Math.min(30, elapsed * 15);
        } else if (elapsed < 8) {
          pct = 30 + (elapsed - 2) * 5;
        } else {
          pct = 60 + Math.min(30, (elapsed - 8) * 1.5);
        }
        pct = Math.min(90, Math.round(pct));
        setProgressPercent(pct);
        if (pct < 35) setProgressLabel(busy === "extract" ? "大模型正在分析业务规则..." : "大模型正在生成测试用例草稿...");
        else if (pct < 65) setProgressLabel(busy === "extract" ? "正在处理 AI 返回内容..." : "正在校验和保存生成结果...");
        else setProgressLabel("正在保存数据...");
      }, 600);
      return () => {
        if (progressTimerRef.current) {
          clearInterval(progressTimerRef.current);
          progressTimerRef.current = null;
        }
      };
    } else if (!busy && progressVisible) {
      if (progressTimerRef.current) {
        clearInterval(progressTimerRef.current);
        progressTimerRef.current = null;
      }
      setProgressPercent(100);
      setProgressLabel("处理完成");
      progressDoneRef.current = setTimeout(() => {
        setProgressVisible(false);
        setProgressPercent(0);
      }, 2000);
      return () => {
        if (progressDoneRef.current) clearTimeout(progressDoneRef.current);
      };
    }
  }, [busy]);
  const [textTitle, setTextTitle] = useState("订单需求");
  const [textContent, setTextContent] = useState(exampleRequirement);
  const [uploadTitle, setUploadTitle] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [documentInfo, setDocumentInfo] = useState<DocumentVO | null>(null);
  const [parseSummary, setParseSummary] = useState<DocumentParseSummaryVO | null>(null);
  const [chunks, setChunks] = useState<DocumentChunkVO[]>([]);
  const [activeChunk, setActiveChunk] = useState<DocumentChunkVO | null>(null);
  const [requirementExtract, setRequirementExtract] = useState<RequirementExtractVO | null>(null);
  const [generationRecord, setGenerationRecord] = useState<GenerationRecordVO | null>(null);
  const [validations, setValidations] = useState<ValidationResultVO[]>([]);
  const [generationResult, setGenerationResult] = useState<TestCaseGenerateVO | null>(null);
  const [drafts, setDrafts] = useState<TestCaseDraftVO[]>([]);
  const [cases, setCases] = useState<TestCaseVO[]>([]);
  const [selectedDraftKeys, setSelectedDraftKeys] = useState<React.Key[]>([]);
  const [editingDraft, setEditingDraft] = useState<TestCaseDraftVO | null>(null);
  const [logSeed, setLogSeed] = useState(0);
  const [logs, setLogs] = useState<OperationLog[]>([]);
  const [caseCount, setCaseCount] = useState(20);
  const [draftForm] = Form.useForm();
  const [diffForm] = Form.useForm();
  const [diffTasks, setDiffTasks] = useState<DiffAnalysisTaskVO[]>([]);
  const [diffSources, setDiffSources] = useState<DiffAnalysisSourceVO[]>([]);
  const [diffBranches, setDiffBranches] = useState<string[]>([]);
  const [diffReport, setDiffReport] = useState<DiffAnalysisReportVO | null>(null);
  const [activeDiffFile, setActiveDiffFile] = useState<DiffChangedFileVO | null>(null);
  const [latestSupplementResult, setLatestSupplementResult] = useState<DiffSupplementCaseVO | null>(null);
  const isDiffView = diffStepKeys.includes(currentStep);

  const documentId = documentInfo?.documentId;
  const generationId = generationResult?.generationId || requirementExtract?.generationId || generationRecord?.generationId;
  const passedValidations = validations.filter((item) => item.status === "PASSED").length;
  const failedValidations = validations.filter((item) => item.status === "FAILED").length;
  const approvedDrafts = drafts.filter((item) => item.reviewStatus === "APPROVED").length;
  const pendingDrafts = drafts.filter((item) => !item.reviewStatus || item.reviewStatus === "PENDING").length;

  const addLog = (action: string, status: OperationLog["status"], detail?: string) => {
    setLogSeed((seed) => {
      const next = seed + 1;
      setLogs((items) => [
        {
          id: next,
          time: new Date().toLocaleTimeString(),
          action,
          status,
          detail
        },
        ...items
      ].slice(0, 8));
      return next;
    });
  };

  const runAction = async <T,>(key: string, label: string, action: () => Promise<T>): Promise<T | null> => {
    setBusy(key);
    try {
      const result = await action();
      messageApi.success(`${label}成功`);
      addLog(label, "success");
      return result;
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      messageApi.error(detail);
      addLog(label, "error", detail);
      return null;
    } finally {
      setBusy(null);
    }
  };

  const createTextDocument = async () => {
    if (textContent.trim().length < 10) {
      messageApi.warning("需求内容至少需要 10 个字。");
      return;
    }
    const data = await runAction("create-text", "创建文本文档", () =>
      api.createTextDocument({ title: textTitle.trim() || "需求文本", content: textContent.trim() })
    );
    if (data) {
      resetAfterDocument(data);
      setCurrentStep("parse");
    }
  };

  const uploadDocument = async () => {
    if (!selectedFile) {
      messageApi.warning("请先选择需求文件。");
      return;
    }
    const data = await runAction("upload", "上传文件", () =>
      api.uploadDocument({ title: uploadTitle.trim() || undefined, file: selectedFile })
    );
    if (data) {
      resetAfterDocument(data);
      setCurrentStep("parse");
    }
  };

  const resetAfterDocument = (document: DocumentVO) => {
    setDocumentInfo(document);
    setParseSummary(null);
    setChunks([]);
    setRequirementExtract(null);
    setGenerationRecord(null);
    setValidations([]);
    setGenerationResult(null);
    setDrafts([]);
    setCases([]);
    setSelectedDraftKeys([]);
  };

  const parseDocument = async () => {
    if (!documentId) {
      messageApi.warning("请先创建或上传文档。");
      setCurrentStep("input");
      return;
    }
    const data = await runAction("parse", "解析文档", () => api.parseDocument(documentId));
    if (data) {
      setParseSummary(data);
      await refreshDocumentAndChunks(documentId);
      setCurrentStep("generate");
    }
  };

  const refreshDocumentAndChunks = async (id = documentId) => {
    if (!id) return;
    const data = await runAction("refresh-document", "刷新解析结果", async () => {
      const [detail, chunkList] = await Promise.all([api.getDocument(id), api.listChunks(id)]);
      return { detail, chunkList };
    });
    if (data) {
      setDocumentInfo(data.detail);
      setChunks(data.chunkList);
    }
  };

  const extractRequirements = async () => {
    if (!documentId) {
      messageApi.warning("请先准备文档。");
      return;
    }
    const data = await runAction("extract", "提取结构化需求", () =>
      api.extractRequirements({
        documentId,
        modelCode: "default",
        promptTemplateCode: "REQUIREMENT_EXTRACT"
      })
    );
    if (data) {
      setRequirementExtract(data);
      setGenerationRecord(null);
    }
  };

  const generateCases = async () => {
    if (!documentId) {
      messageApi.warning("请先准备文档。");
      return;
    }
    const data = await runAction("generate", "生成测试用例草稿", () =>
      api.generateCases({
        documentId,
        requirementExtractId: requirementExtract?.requirementExtractId,
        modelCode: "default",
        promptTemplateCode: "TEST_CASE_GENERATE",
        caseCount
      })
    );
    if (data) {
      setGenerationResult(data);
      setDrafts(data.drafts || []);
      setCurrentStep("review");
      await refreshValidations(data.generationId);
    }
  };

  const refreshGeneration = async () => {
    if (!generationId) {
      messageApi.warning("暂无 Generation ID。");
      return;
    }
    const data = await runAction("generation", "查询生成记录", () => api.getGeneration(generationId));
    if (data) setGenerationRecord(data);
  };

  const refreshValidations = async (id = generationId) => {
    if (!id) return;
    const data = await runAction("validations", "查询校验结果", () => api.listValidations(id));
    if (data) setValidations(data);
  };

  const refreshDrafts = async (options?: { documentOnly?: boolean }) => {
    const data = await runAction("drafts", "查询草稿", () =>
      api.listDrafts({
        documentId,
        generationId: options?.documentOnly ? undefined : generationResult?.generationId
      })
    );
    if (data) {
      setDrafts(data);
      setSelectedDraftKeys([]);
    }
  };

  const refreshCases = async () => {
    const queryInfo = { documentId, requirementExtractId: requirementExtract?.requirementExtractId };
    console.log("refreshCases query:", queryInfo);
    
    try {
      const data = await runAction("cases", "查询正式用例", () =>
        api.listCases(queryInfo)
      );
      
      if (data !== null) {
        console.log("refreshCases success:", data.length, "cases");
        setCases(data);
      } else {
        console.log("refreshCases failed, data is null");
      }
    } catch (error) {
      console.error("refreshCases error:", error);
    }
  };

  const handleMenuClick: MenuProps["onClick"] = ({ key }) => {
    if (key === "diff-menu") {
      return;
    }
    setCurrentStep(key as StepKey);
  };

  const createDiffTask = async () => {
    const values = await diffForm.validateFields();
    const data = await runAction("diff-create", "创建 Diff 分析任务", () =>
      api.createDiffTask({
        documentId: values.documentId || documentId,
        requirementExtractId: values.requirementExtractId || requirementExtract?.requirementExtractId,
        repoUrl: values.repoUrl,
        sourceBranch: values.sourceBranch,
        targetBranch: values.targetBranch || "master",
        analysisOptions: {
          includeTestFiles: Boolean(values.includeTestFiles),
          enableAiAnalysis: Boolean(values.enableAiAnalysis),
          enableCoverageCheck: Boolean(values.enableCoverageCheck),
          autoGenerateSupplementCases: Boolean(values.autoGenerateSupplementCases)
        }
      })
    );
    if (data) {
      await refreshDiffTasks();
      await loadDiffReport(data.taskId);
    }
  };

  const refreshDiffTasks = async () => {
    const selectedDocumentId = diffForm.getFieldValue("documentId") || documentId;
    const selectedRequirementExtractId = diffForm.getFieldValue("requirementExtractId") || requirementExtract?.requirementExtractId;
    const data = await runAction("diff-list", "查询 Diff 分析任务", () =>
      api.listDiffTasks({ documentId: selectedDocumentId, requirementExtractId: selectedRequirementExtractId })
    );
    if (data) setDiffTasks(data);
  };

  const refreshDiffSources = async () => {
    const data = await runAction("diff-sources", "查询最近需求来源", () => api.listDiffSources(10));
    if (data) {
      setDiffSources(data);
      const currentDocumentId = diffForm.getFieldValue("documentId") || documentId;
      if (!currentDocumentId && data[0]) {
        diffForm.setFieldsValue({
          documentId: data[0].documentId,
          requirementExtractId: data[0].requirementExtractId
        });
      }
    }
  };

  const refreshRepositoryBranches = async () => {
    const repoUrl = diffForm.getFieldValue("repoUrl");
    if (!repoUrl) {
      messageApi.warning("请先输入仓库地址");
      return;
    }
    const data = await runAction("diff-branches", "查询仓库分支", () => api.listRepositoryBranches(repoUrl));
    if (data) {
      setDiffBranches(data);
      if (!diffForm.getFieldValue("targetBranch")) {
        diffForm.setFieldValue("targetBranch", data.includes("master") ? "master" : data.includes("main") ? "main" : data[0]);
      }
    }
  };

  const loadDiffReport = async (taskId: number) => {
    if (diffReport?.task.taskId !== taskId) {
      setLatestSupplementResult(null);
    }
    const data = await runAction("diff-report", "查询 Diff 分析报告", () => api.getDiffReport(taskId));
    if (data) setDiffReport(data);
  };
  const refreshCurrentDiffView = async () => {
    if (!diffReport?.task.taskId) return;
    await loadDiffReport(diffReport.task.taskId);
    await refreshDiffTasks();
  };

  const openSupplementDrafts = async () => {
    await refreshDrafts({ documentOnly: true });
    setCurrentStep("review");
  };

  const applyRiskAction = async (risk: DiffRiskItemVO, actionType: string) => {
    const data = await runAction("diff-risk-action", "更新风险状态", () =>
      api.applyDiffRiskAction(risk.riskId, {
        actionType,
        actionDesc: actionType === "IGNORE" ? "页面忽略" : "页面操作",
        ignoreReason: actionType === "IGNORE" ? "人工判断本次无需处理" : undefined,
        operator: "manual_user"
      })
    );
    if (data !== null) {
      await refreshCurrentDiffView();
    }
  };

  const generateSupplementCases = async (risk: DiffRiskItemVO) => {
    const data = await runAction("diff-supplement", "生成补充用例", () => api.generateDiffSupplementCases(risk.riskId));
    if (data) {
      setLatestSupplementResult(data);
      await refreshCurrentDiffView();
      await refreshDrafts({ documentOnly: true });
      messageApi.success(`已生成 ${data.generatedCases.length} 条补充用例草稿`);
    }
  };
  const saveDraft = async () => {
    if (!editingDraft) return;
    const values = await draftForm.validateFields();
    const riskLevel = values.riskLevel || editingDraft.riskLevel || "P1";
    const caseType = toEnglishCaseType(values.caseType) || toEnglishCaseType(getDefaultCaseTypeForRiskLevel(riskLevel)) || "NORMAL";
    const data = await runAction("save-draft", "保存草稿编辑", () =>
      api.updateDraft(editingDraft.draftCaseId, {
        title: values.title,
        preconditionsJson: normalizeArray(values.preconditionsJson),
        stepsJson: normalizeArray(values.stepsJson),
        expectedResultsJson: normalizeArray(values.expectedResultsJson),
        priority: values.priority,
        caseType,
        riskLevel,
        requirementRefsJson: normalizeArray(values.requirementRefsJson),
        riskTagsJson: normalizeArray(values.riskTagsJson),
        reviewer: "manual_user"
      })
    );
    if (data) {
      setDrafts((items) => items.map((item) => (item.draftCaseId === data.draftCaseId ? data : item)));
      setEditingDraft(null);
    }
  };

  const approveDraft = async (draft: TestCaseDraftVO) => {
    console.log("approveDraft starting:", draft.draftCaseId);
    const data = await runAction("approve-draft", "确认草稿", () => api.approveDraft(draft.draftCaseId));
    console.log("approveDraft result:", data);
    if (data) {
      console.log("approveDraft success, refreshing drafts and cases...");
      await refreshDrafts();
      await refreshCases();
      console.log("refresh completed, cases count:", cases.length);
    }
  };

  const rejectDraft = async (draft: TestCaseDraftVO) => {
    const data = await runAction("reject-draft", "驳回草稿", () => api.rejectDraft(draft.draftCaseId));
    if (data) {
      setDrafts((items) => items.map((item) => (item.draftCaseId === data.draftCaseId ? data : item)));
    }
  };

  const batchApprove = async () => {
    const ids = selectedDraftKeys.map(String);
    if (!ids.length) {
      messageApi.warning("请先选择待确认草稿。");
      return;
    }
    const data = await runAction("batch-approve", "批量确认", () => api.batchApprove(ids));
    if (data) {
      await refreshDrafts();
      await refreshCases();
    }
  };

  const uploadProps: UploadProps = {
    accept: allowedExtensions.map((item) => `.${item}`).join(","),
    multiple: false,
    maxCount: 1,
    showUploadList: false,
    beforeUpload: (file) => {
      const validation = validateFile(file);
      if (validation) {
        messageApi.error(validation);
        return Upload.LIST_IGNORE;
      }
      setSelectedFile(file);
      return false;
    },
    onRemove: () => {
      setSelectedFile(null);
    }
  };

  const chunkColumns: ColumnsType<DocumentChunkVO> = [
    { title: "Chunk ID", dataIndex: "chunkId", width: 180, fixed: "left" },
    { title: "序号", dataIndex: "chunkIndex", width: 80, sorter: (a, b) => a.chunkIndex - b.chunkIndex },
    { title: "所属章节", dataIndex: "sectionTitle", width: 160, render: (value) => value || "默认章节" },
    { title: "字数", width: 90, render: (_, record) => record.chunkText?.length || 0 },
    { title: "Token 估算", dataIndex: "tokenCount", width: 120, render: (value) => value || 0 },
    {
      title: "内容预览",
      dataIndex: "chunkText",
      render: (value: string) => <Text ellipsis>{value || "暂无内容"}</Text>
    },
    {
      title: "操作",
      width: 90,
      render: (_, record) => (
        <Button size="small" onClick={() => setActiveChunk(record)}>
          查看
        </Button>
      )
    }
  ];

  const draftColumns: ColumnsType<TestCaseDraftVO> = [
    {
      title: "状态",
      dataIndex: "reviewStatus",
      width: 110,
      render: (value) => <StatusTag value={value} />
    },
    { title: "用例标题", dataIndex: "title", width: 260, render: (value) => <Text strong>{value}</Text> },
    { title: "优先级", dataIndex: "priority", width: 90, render: (value) => <Tag color="blue">{value || "-"}</Tag> },
    { title: "类型", dataIndex: "caseType", width: 130, render: (value) => <Tag color="purple">{formatCaseType(value)}</Tag> },
    { title: "风险等级", dataIndex: "riskLevel", width: 110, render: (value) => <RiskTag value={value} /> },
    { title: "关联需求", dataIndex: "requirementRefsJson", width: 180, render: (value) => renderJsonTags(value) },
    {
      title: "操作",
      width: 230,
      fixed: "right",
      render: (_, record) => (
        <Space wrap>
          <Button size="small" icon={<EditOutlined />} onClick={() => openDraftEditor(record)}>
            编辑
          </Button>
          <Button size="small" type="primary" onClick={() => approveDraft(record)}>
            确认
          </Button>
          <Button size="small" danger onClick={() => rejectDraft(record)}>
            驳回
          </Button>
        </Space>
      )
    }
  ];

  const caseColumns: ColumnsType<TestCaseVO> = [
    { title: "Case ID", dataIndex: "caseId", width: 130 },
    { title: "标题", dataIndex: "title", width: 280, render: (value) => <Text strong>{value}</Text> },
    { title: "优先级", dataIndex: "priority", width: 90, render: (value) => <Tag color="blue">{value || "-"}</Tag> },
    { title: "类型", dataIndex: "caseType", width: 130, render: (value) => <Tag color="purple">{formatCaseType(value)}</Tag> },
    { title: "风险", dataIndex: "riskLevel", width: 100, render: (value) => <RiskTag value={value} /> },
    { title: "状态", dataIndex: "status", width: 100, render: (value) => <Tag color="green">{value || "ACTIVE"}</Tag> },
    { title: "关联需求", dataIndex: "requirementRefsJson", render: (value) => renderJsonTags(value) }
  ];

  const steps = useMemo(
    () =>
      [
        { key: "input", title: "输入材料", icon: <CloudUploadOutlined /> },
        { key: "parse", title: "解析结果", icon: <FileSearchOutlined /> },
        { key: "generate", title: "AI 生成", icon: <RobotOutlined /> },
        { key: "review", title: "人工确认", icon: <EditOutlined /> },
        { key: "export", title: "保存导出", icon: <DownloadOutlined /> }
      ].map((item) => ({ ...item, status: getStepStatus(item.key as StepKey, currentStep, documentInfo, chunks, drafts, cases) })),
    [cases, chunks, currentStep, documentInfo, drafts]
  );

  return (
    <Layout className="app-shell">
      {contextHolder}
      <Header className="app-header">
        <div>
          <Title level={1}>AI 测试设计工作台</Title>
          <Text type="secondary">后台管理系统 + AI 工作台 + 数据卡片</Text>
        </div>
        <div className="header-meta">
          <div className="header-meta-item">
            <Text type="secondary">当前项目</Text>
            <Text strong>{documentInfo?.title || "Demo 项目"}</Text>
          </div>
          <div className="header-meta-item">
            <Text type="secondary">当前模型</Text>
            <Tag color="blue">default</Tag>
          </div>
          <div className="header-meta-item">
            <Text type="secondary">流程状态</Text>
            <StatusTag value={cases.length ? "ACTIVE" : documentInfo?.parseStatus} />
          </div>
        </div>
      </Header>

      <Layout className="app-body">
        <Sider width={224} className="app-sider" breakpoint="lg" collapsedWidth={0}>
          <Menu
            mode="inline"
            selectedKeys={[currentStep]}
            defaultOpenKeys={["diff-menu"]}
            items={menuItems}
            onClick={handleMenuClick}
          />
        </Sider>

        <Content className="app-content">
          <div className="content-grid">
            <main className="workbench-main">
              <Card className="step-card">
                {isDiffView ? (
                  <Space>
                    <CodeOutlined />
                    <Text strong>{currentStep === "diff" ? "代码 Diff 分析与测试覆盖风险识别" : "变更文件和风险覆盖分析"}</Text>
                  </Space>
                ) : (
                  <Steps
                    current={stepKeys.indexOf(currentStep)}
                    items={steps}
                    onChange={(index) => setCurrentStep(stepKeys[index])}
                    responsive
                  />
                )}
              </Card>
              {renderMetricStrip()}

              <Spin spinning={busy !== null && busy !== "extract" && busy !== "generate"} tip={loadingText(busy)}>
                {currentStep === "input" && renderInputStep()}
                {currentStep === "parse" && renderParseStep()}
                {currentStep === "generate" && renderGenerateStep()}
                {currentStep === "review" && renderReviewStep()}
                {currentStep === "export" && renderExportStep()}
                {currentStep === "diff" && renderDiffStep()}
                {currentStep === "diff-detail" && renderDiffDetailStep()}
              </Spin>
            </main>

            <aside className="workbench-side">
              {renderSummaryPanel()}
              {renderLogPanel()}
            </aside>
          </div>
        </Content>
      </Layout>

      <footer className="app-footer">
        <div className="footer-content">
          <div className="footer-section">
            <span className="footer-label">自动化测试平台</span>
            <a href="https://autotest.wiac.xyz/" target="_blank" rel="noopener noreferrer">https://autotest.wiac.xyz/</a>
          </div>
          <div className="footer-divider" />
          <div className="footer-section">
            <span className="footer-label">GitHub</span>
            <a href="https://github.com/acai1998/AI-TestOps" target="_blank" rel="noopener noreferrer">https://github.com/acai1998/AI-TestOps</a>
          </div>
        </div>
      </footer>

      <Drawer
        title="编辑测试用例草稿"
        width={560}
        open={Boolean(editingDraft)}
        onClose={() => setEditingDraft(null)}
        extra={
          <Space>
            <Button onClick={() => setEditingDraft(null)}>取消</Button>
            <Button type="primary" icon={<SaveOutlined />} loading={busy === "save-draft"} onClick={saveDraft}>
              保存
            </Button>
          </Space>
        }
      >
        {editingDraft && (
          <Form
            form={draftForm}
            layout="vertical"
            initialValues={draftToForm(editingDraft)}
            key={editingDraft.draftCaseId}
            onValuesChange={(changedValues) => {
              if (changedValues.riskLevel) {
                draftForm.setFieldValue("caseType", getDefaultCaseTypeForRiskLevel(changedValues.riskLevel));
              }
            }}
          >
            <Form.Item label="用例标题" name="title" rules={[{ required: true, message: "请输入用例标题" }]}>
              <Input maxLength={255} showCount />
            </Form.Item>
            <Row gutter={12}>
              <Col span={8}>
                <Form.Item label="优先级" name="priority" rules={[{ required: true }]}>
                  <Select options={["P0", "P1", "P2", "P3"].map((value) => ({ value, label: value }))} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item label="类型" name="caseType">
                  <Select options={CASE_TYPE_OPTIONS} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item label="风险等级" name="riskLevel">
                  <Select options={[{ value: "P0", label: "P0" }, { value: "P1", label: "P1" }, { value: "P2", label: "P2" }]} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item label="前置条件 JSON" name="preconditionsJson" rules={[jsonArrayRule("前置条件", { allowEmpty: true })]}>
              <TextArea rows={4} />
            </Form.Item>
            <Form.Item label="测试步骤 JSON" name="stepsJson" rules={[jsonArrayRule("测试步骤", { allowEmpty: false })]}>
              <TextArea rows={8} />
            </Form.Item>
            <Form.Item label="预期结果 JSON" name="expectedResultsJson" rules={[jsonArrayRule("预期结果", { allowEmpty: false })]}>
              <TextArea rows={4} />
            </Form.Item>
            <Form.Item label="关联需求 JSON" name="requirementRefsJson" rules={[jsonArrayRule("关联需求", { allowEmpty: true })]}>
              <TextArea rows={3} />
            </Form.Item>
            <Form.Item label="风险标签 JSON" name="riskTagsJson" rules={[jsonArrayRule("风险标签", { allowEmpty: true })]}>
              <TextArea rows={3} />
            </Form.Item>
          </Form>
        )}
      </Drawer>

      <Drawer title={activeChunk?.chunkId} width={620} open={Boolean(activeChunk)} onClose={() => setActiveChunk(null)}>
        <Paragraph copyable className="chunk-fulltext">
          {activeChunk?.chunkText || "该 chunk 暂无文本。"}
        </Paragraph>
      </Drawer>
    </Layout>
  );

  function renderInputStep() {
    return (
      <Card className="panel-card" title="输入材料" extra={<Tag color="blue">支持文本和文件</Tag>}>
        <Tabs
          items={[
            {
              key: "text",
              label: "粘贴需求文本",
              children: (
                <div className="stack">
                  <Input value={textTitle} maxLength={255} showCount onChange={(event) => setTextTitle(event.target.value)} />
                  <TextArea
                    value={textContent}
                    rows={12}
                    showCount
                    onChange={(event) => setTextContent(event.target.value)}
                    placeholder="粘贴需求文档内容"
                  />
                  <Alert
                    type={textContent.length >= 500 ? "success" : "info"}
                    showIcon
                    message={`当前输入 ${textContent.length} 字，需求内容超过 500 字后 AI 生成效果通常更稳定。`}
                  />
                  <Space wrap>
                    <Button icon={<CodeOutlined />} onClick={() => setTextContent(exampleRequirement)}>
                      填充示例
                    </Button>
                    <Button onClick={() => setTextContent("")}>清空</Button>
                    <Button type="primary" icon={<FileDoneOutlined />} loading={busy === "create-text"} onClick={createTextDocument}>
                      创建文本文档
                    </Button>
                  </Space>
                  <Card size="small" title="内容预览" className="soft-card">
                    <Paragraph ellipsis={{ rows: 4, expandable: true }}>{textContent || "暂无需求文本。"}</Paragraph>
                  </Card>
                </div>
              )
            },
            {
              key: "file",
              label: "上传需求文件",
              children: (
                <div className="stack">
                  <Input value={uploadTitle} maxLength={255} showCount placeholder="可选，默认使用文件名" onChange={(event) => setUploadTitle(event.target.value)} />
                  <Dragger {...uploadProps}>
                    <p className="ant-upload-drag-icon">
                      <CloudUploadOutlined />
                    </p>
                    <p className="ant-upload-text">拖拽文件到此处，或点击选择文件</p>
                    <p className="ant-upload-hint">支持 md / txt / doc / docx / pdf / ppt / pptx / xls / xlsx，单文件最大 50MB</p>
                  </Dragger>
                  {selectedFile ? <FileCard file={selectedFile} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无需求文件" />}
                  <Button type="primary" icon={<CloudUploadOutlined />} loading={busy === "upload"} onClick={uploadDocument}>
                    上传并创建文档
                  </Button>
                </div>
              )
            }
          ]}
        />
      </Card>
    );
  }

  function renderParseStep() {
    return (
      <div className="stack">
        <Card
          className="panel-card"
          title="解析结果"
          extra={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => refreshDocumentAndChunks()}>
                刷新
              </Button>
              <Button type="primary" icon={<PlayCircleOutlined />} loading={busy === "parse"} onClick={parseDocument}>
                解析文档
              </Button>
            </Space>
          }
        >
          {!documentInfo ? (
            <Empty description="暂无需求材料，请先创建或上传文档。" />
          ) : (
            <div className="stack">
              <DocumentDescriptions documentInfo={documentInfo} parseSummary={parseSummary} />
              <Row gutter={[16, 16]}>
                <Col xs={24} lg={12}>
                  <Card size="small" title="元数据" className="soft-card">
                    <pre className="json-box">{formatJson(documentInfo.metadataJson || parseSummary?.metadataJson)}</pre>
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card size="small" title="正文预览" className="soft-card">
                    <Paragraph copyable ellipsis={{ rows: 8, expandable: true }}>
                      {documentInfo.rawTextSummary || "解析完成后会展示正文摘要。"}
                    </Paragraph>
                  </Card>
                </Col>
              </Row>
            </div>
          )}
        </Card>
        <Card className="panel-card" title="Chunk 分块结果" extra={<Tag>{chunks.length} 个 chunks</Tag>}>
          <Table<DocumentChunkVO>
            rowKey="chunkId"
            columns={chunkColumns}
            dataSource={chunks}
            locale={{ emptyText: <Empty description="暂无 chunk 数据，请先解析文档。" /> }}
            pagination={{ pageSize: 6 }}
            scroll={{ x: 760 }}
          />
        </Card>
      </div>
    );
  }

  function renderGenerateStep() {
    return (
      <div className="stack">
        <Card className="panel-card" title="生成配置">
          <Row gutter={[16, 16]}>
            <Col xs={24} md={8}>
              <Text type="secondary">模型</Text>
              <Select className="full-width" value="default" options={[{ value: "default", label: "default" }]} />
            </Col>
            <Col xs={24} md={8}>
              <Text type="secondary">Prompt 模板</Text>
              <Select
                className="full-width"
                value="TEST_CASE_GENERATE"
                options={[{ value: "TEST_CASE_GENERATE", label: "测试用例生成 v1.0.0" }]}
              />
            </Col>
            <Col xs={24} md={8}>
              <Text type="secondary">用例数量</Text>
              <InputNumber className="full-width" value={caseCount} min={1} max={50} onChange={(val) => setCaseCount(val ?? 20)} />
            </Col>
          </Row>
          <Divider />
          <Checkbox.Group
            value={["功能", "接口", "字段", "权限", "异常", "边界"]}
            options={["功能", "接口", "字段", "权限", "异常", "边界"]}
          />
        </Card>
        <Card
          className="panel-card"
          title="AI 生成"
          extra={
            <Space wrap>
              <Button icon={<ExperimentOutlined />} disabled={busy === "extract"} onClick={extractRequirements}>
                提取结构化需求
              </Button>
              <Button type="primary" icon={<RobotOutlined />} disabled={busy === "generate"} onClick={generateCases}>
                生成测试用例草稿
              </Button>
              <Button icon={<DatabaseOutlined />} onClick={refreshGeneration}>
                查询生成记录
              </Button>
            </Space>
          }
        >
          <Row gutter={[16, 16]}>
            <Col xs={24} md={8}>
              <Statistic title="结构化需求" value={countJsonItems(requirementExtract?.requirementsJson)} suffix="条" />
            </Col>
            <Col xs={24} md={8}>
              <Statistic title="生成草稿" value={generationResult?.draftCount || drafts.length} suffix="条" />
            </Col>
            <Col xs={24} md={8}>
              <Statistic title="校验失败" value={failedValidations} suffix="项" />
            </Col>
          </Row>
          <Divider />
          {progressVisible && (
            <div className="progress-wrapper">
              <Progress
                percent={progressPercent}
                status={progressPercent === 100 ? "success" : "active"}
                strokeColor={{ from: "#108ee9", to: "#87d068" }}
              />
              <Text type="secondary" className="progress-label">
                {progressLabel}
              </Text>
            </div>
          )}
          <Alert
            showIcon
            type={generationResult ? "success" : "info"}
            message={generationResult ? "测试用例草稿已生成，可进入人工确认。" : "建议先提取结构化需求，再生成测试用例草稿。"}
          />
          {validations.length > 0 && (
            <List
              className="validation-list"
              dataSource={validations}
              renderItem={(item) => (
                <List.Item>
                  <Space>
                    <Tag color={item.status === "PASSED" ? "green" : "red"}>{item.status}</Tag>
                    <Text>{item.validationType}</Text>
                    <Text type="secondary">{formatJson(item.errorDetailJson)}</Text>
                  </Space>
                </List.Item>
              )}
            />
          )}
        </Card>
      </div>
    );
  }

  function renderReviewStep() {
    return (
      <Card
        className="panel-card"
        title="人工确认"
        extra={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => refreshDrafts()}>
              查询草稿
            </Button>
            <Button icon={<CheckCircleOutlined />} type="primary" onClick={batchApprove}>
              批量确认
            </Button>
          </Space>
        }
      >
        <Table<TestCaseDraftVO>
          rowKey="draftCaseId"
          columns={draftColumns}
          dataSource={drafts}
          rowSelection={{ selectedRowKeys: selectedDraftKeys, onChange: setSelectedDraftKeys }}
          locale={{ emptyText: <Empty description="暂无测试用例草稿，请先生成。" /> }}
          pagination={{ pageSize: 8 }}
          scroll={{ x: 980 }}
        />
      </Card>
    );
  }

  function renderExportStep() {
    return (
      <div className="stack">
        <Card
          className="panel-card"
          title="正式测试用例"
          extra={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={refreshCases}>
                查询正式用例
              </Button>
              <Button icon={<DownloadOutlined />} onClick={() => window.open(api.exportJsonUrl(documentId), "_blank")}>
                导出 JSON
              </Button>
              <Button type="primary" icon={<DownloadOutlined />} onClick={() => window.open(api.exportExcelUrl(documentId), "_blank")}>
                导出 Excel
              </Button>
            </Space>
          }
        >
          <Table<TestCaseVO>
            rowKey="testCaseId"
            columns={caseColumns}
            dataSource={cases}
            locale={{ emptyText: <Empty description="暂无正式测试用例，请先确认草稿。" /> }}
            pagination={{ pageSize: 8 }}
            scroll={{ x: 900 }}
          />
        </Card>
      </div>
    );
  }

  function renderSummaryPanel() {
    return (
      <Card title="当前结果摘要" className="side-card">
        <Space direction="vertical" className="full-width" size="middle">
          <div className="summary-head">
            <Badge status={busy ? "processing" : "default"} />
            <Text strong>{busy ? loadingText(busy) : "等待操作"}</Text>
          </div>
          <SummaryItem label="文档状态" value={documentInfo?.parseStatus || "未开始"} status={documentInfo?.parseStatus} />
          <SummaryItem label="文本长度" value={`${documentInfo?.rawTextLength || parseSummary?.rawTextLength || 0}`} />
          <SummaryItem label="Chunk 数量" value={`${chunks.length || parseSummary?.chunkCount || 0}`} />
          <SummaryItem label="待确认草稿" value={`${pendingDrafts}`} status={pendingDrafts ? "PENDING" : undefined} />
          <SummaryItem label="已确认草稿" value={`${approvedDrafts}`} status={approvedDrafts ? "APPROVED" : undefined} />
          <SummaryItem label="正式用例" value={`${cases.length}`} />
        </Space>
      </Card>
    );
  }

  function renderMetricStrip() {
    const metrics = [
      {
        label: "文档解析结果",
        value: documentInfo?.rawTextLength || parseSummary?.rawTextLength || 0,
        suffix: "字",
        icon: <FileSearchOutlined />,
        color: "blue"
      },
      {
        label: "Chunk 数量",
        value: chunks.length || parseSummary?.chunkCount || 0,
        suffix: "个",
        icon: <DatabaseOutlined />,
        color: "green"
      },
      {
        label: "AI 生成草稿",
        value: generationResult?.draftCount || drafts.length,
        suffix: "条",
        icon: <RobotOutlined />,
        color: "purple"
      },
      {
        label: "校验通过",
        value: passedValidations,
        suffix: failedValidations ? `项 / 失败 ${failedValidations}` : "项",
        icon: <BarChartOutlined />,
        color: failedValidations ? "orange" : "cyan"
      },
      {
        label: "待确认",
        value: pendingDrafts,
        suffix: "条",
        icon: <ClockCircleOutlined />,
        color: pendingDrafts ? "gold" : "default"
      }
    ];

    return (
      <div className="metric-strip">
        {metrics.map((item) => (
          <div className={`metric-card metric-${item.color}`} key={item.label}>
            <div className="metric-icon">{item.icon}</div>
            <div>
              <Text type="secondary">{item.label}</Text>
              <div className="metric-value">
                <span>{item.value}</span>
                <Text type="secondary">{item.suffix}</Text>
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  function renderLogPanel() {
    return (
      <Card title="操作记录" className="side-card">
        {logs.length ? (
          <List
            dataSource={logs}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  avatar={<Badge status={item.status === "success" ? "success" : item.status === "error" ? "error" : "processing"} />}
                  title={`${item.time} ${item.action}`}
                  description={item.detail || "完成"}
                />
              </List.Item>
            )}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作记录" />
        )}
      </Card>
    );
  }

  function openDraftEditor(draft: TestCaseDraftVO) {
    setEditingDraft(draft);
    draftForm.setFieldsValue(draftToForm(draft));
  }

  function renderDiffStep() {
    const sourceOptions = diffSources.map((source) => ({
      value: source.documentId,
      label: `${source.documentTitle || source.documentId} / ${source.requirementExtractId || "暂无需求解析结果"}`
    }));
    const branchOptions = diffBranches.map((branch) => ({ value: branch }));

    const taskColumns: ColumnsType<DiffAnalysisTaskVO> = [
      { title: "任务编码", dataIndex: "taskCode", width: 180 },
      { title: "仓库", dataIndex: "repoName", width: 160, render: (value, record) => value || record.repoUrl },
      { title: "源分支", dataIndex: "sourceBranch", width: 180 },
      { title: "目标分支", dataIndex: "targetBranch", width: 120 },
      { title: "状态", dataIndex: "status", width: 110, render: (value) => <StatusTag value={value} /> },
      { title: "准入", dataIndex: "mergeGateStatus", width: 130, render: (value) => <GateTag value={value} /> },
      { title: "高风险", dataIndex: "highRiskCount", width: 90 },
      { title: "未覆盖", dataIndex: "notCoveredRiskCount", width: 90 },
      {
        title: "操作",
        width: 120,
        fixed: "right",
        render: (_, record) => (
          <Button
            size="small"
            onClick={async () => {
              await loadDiffReport(record.taskId);
              setCurrentStep("diff");
            }}
          >
            {"查看报告"}
          </Button>
        )
      }
    ];

    return (
      <div className="stack">
        <Card className="panel-card" title="创建 Diff 分析任务">
          <Form
            form={diffForm}
            layout="vertical"
            initialValues={{
              documentId,
              requirementExtractId: requirementExtract?.requirementExtractId,
              targetBranch: "master",
              includeTestFiles: false,
              enableAiAnalysis: true,
              enableCoverageCheck: true,
              autoGenerateSupplementCases: false
            }}
          >
            <Row gutter={[16, 0]}>
              <Col xs={24}>
                <Form.Item label="最近需求来源">
                  <Select
                    allowClear
                    showSearch
                    placeholder="可选择最近一次文档和需求解析结果"
                    options={sourceOptions}
                    optionFilterProp="label"
                    onChange={(value) => {
                      const source = diffSources.find((item) => item.documentId === value);
                      if (source) {
                        diffForm.setFieldsValue({
                          documentId: source.documentId,
                          requirementExtractId: source.requirementExtractId
                        });
                      }
                    }}
                    dropdownRender={(menu) => (
                      <>
                        {menu}
                        <Divider style={{ margin: "8px 0" }} />
                        <Button type="link" icon={<ReloadOutlined />} loading={busy === "diff-sources"} onClick={refreshDiffSources}>
                          {"刷新最近来源"}
                        </Button>
                      </>
                    )}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="documentId" label="关联文档">
                  <Input placeholder={documentId || "留空时自动使用最近一次需求来源"} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="requirementExtractId" label="需求解析结果">
                  <Input placeholder={requirementExtract?.requirementExtractId || "留空时自动匹配文档最近一次解析结果"} />
                </Form.Item>
              </Col>
              <Col xs={24}>
                <Form.Item name="repoUrl" label="仓库地址" rules={[{ required: true, message: "请输入 Git 仓库地址或本地仓库路径" }]}>
                  <Input placeholder="https://github.com/acai1998/AI-TestOps.git" />
                </Form.Item>
                <Button icon={<ReloadOutlined />} loading={busy === "diff-branches"} onClick={refreshRepositoryBranches}>
                  {"获取仓库分支"}
                </Button>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="sourceBranch" label="变更分支（待分析）" rules={[{ required: true, message: "请输入变更分支" }]}>
                  <AutoComplete options={branchOptions} placeholder="feature/diff-analysis-task" filterOption />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="targetBranch" label="基准分支（主分支）">
                  <AutoComplete options={branchOptions} placeholder="master 或 main" filterOption />
                </Form.Item>
              </Col>
              <Col xs={24}>
                <Space wrap>
                  <Form.Item name="includeTestFiles" valuePropName="checked" noStyle>
                    <Checkbox>{"包含测试文件"}</Checkbox>
                  </Form.Item>
                  <Form.Item name="enableAiAnalysis" valuePropName="checked" noStyle>
                    <Checkbox>{"启用 AI 风险分析"}</Checkbox>
                  </Form.Item>
                  <Form.Item name="enableCoverageCheck" valuePropName="checked" noStyle>
                    <Checkbox>{"启用用例覆盖匹配"}</Checkbox>
                  </Form.Item>
                  <Form.Item name="autoGenerateSupplementCases" valuePropName="checked" noStyle>
                    <Checkbox>{"自动生成补充用例"}</Checkbox>
                  </Form.Item>
                </Space>
              </Col>
            </Row>
            <Divider />
            <Space wrap>
              <Button type="primary" icon={<PlayCircleOutlined />} loading={busy === "diff-create"} onClick={createDiffTask}>
                {"开始分析"}
              </Button>
              <Button icon={<ReloadOutlined />} onClick={refreshDiffTasks}>
                {"刷新任务"}
              </Button>
            </Space>
          </Form>
        </Card>

        <Card className="panel-card" title="Diff 分析任务">
          <Table<DiffAnalysisTaskVO>
            rowKey="taskId"
            columns={taskColumns}
            dataSource={diffTasks}
            locale={{ emptyText: <Empty description="暂无 Diff 分析任务" /> }}
            pagination={{ pageSize: 6 }}
            scroll={{ x: 1100 }}
          />
        </Card>

        {diffReport && (
          <Card
            className="panel-card"
            title="合并准入报告"
            extra={
              <Button type="link" onClick={() => setCurrentStep("diff-detail")}>
                {"查看更多"}
              </Button>
            }
          >
            <Row gutter={[16, 16]}>
              <Col xs={12} md={6}>
                <Statistic title="准入结论" value={diffReport.report?.gateStatus || diffReport.task.mergeGateStatus || "-"} />
              </Col>
              <Col xs={12} md={6}>
                <Statistic title="变更文件" value={diffReport.report?.changedFileCount || diffReport.changedFiles.length} suffix="个" />
              </Col>
              <Col xs={12} md={6}>
                <Statistic title="高风险" value={diffReport.report?.highRiskCount || 0} suffix="项" />
              </Col>
              <Col xs={12} md={6}>
                <Statistic title="未覆盖" value={diffReport.report?.notCoveredRiskCount || 0} suffix="项" />
              </Col>
            </Row>
            <Divider />
            <Alert type={diffReport.report?.gateStatus === "BLOCK" ? "error" : "info"} showIcon message={diffReport.report?.gateReason || "暂无准入原因"} />
          </Card>
        )}
      </div>
    );
  }

  function renderDiffDetailStep() {
    if (!diffReport) {
      return (
        <Card
          className="panel-card"
          title="变更文件和风险覆盖分析"
          extra={
            <Button type="link" onClick={() => setCurrentStep("diff")}>
              {"返回任务与准入报告"}
            </Button>
          }
        >
          <Empty description="请先在任务与准入报告页面选择一条 Diff 分析报告" />
        </Card>
      );
    }

    const changedFileColumns: ColumnsType<DiffChangedFileVO> = [
      { title: "文件路径", dataIndex: "newFilePath", render: (value) => <Text>{value}</Text> },
      { title: "类型", dataIndex: "changeType", width: 110, render: (value) => <Tag>{value}</Tag> },
      { title: "角色", dataIndex: "fileRole", width: 130, render: (value) => <Tag color="blue">{value || "-"}</Tag> },
      { title: "行数", width: 110, render: (_, record) => <Text>+{record.additions} / -{record.deletions}</Text> },
      { title: "初判", dataIndex: "initialRiskLevel", width: 110, render: (value) => <RiskLevelTag value={value} /> },
      {
        title: "操作",
        width: 90,
        render: (_, record) => (
          <Button size="small" onClick={() => setActiveDiffFile(record)}>
            查看变更
          </Button>
        )
      }
    ];

    const riskColumns: ColumnsType<DiffRiskItemVO> = [
      { title: "风险标题", dataIndex: "riskTitle", width: 280, render: (value) => <Text strong>{value}</Text> },
      { title: "等级", dataIndex: "riskLevel", width: 100, render: (value) => <RiskLevelTag value={value} /> },
      { title: "分类", dataIndex: "riskCategory", width: 120, render: (value) => <Tag>{value}</Tag> },
      { title: "覆盖", dataIndex: "coverageStatus", width: 140, render: (value) => <CoverageTag value={value} /> },
      { title: "处理", dataIndex: "processStatus", width: 120, render: (value) => <StatusTag value={value} /> },
      {
        title: "匹配用例",
        width: 180,
        render: (_, record) => record.matchedCases?.length ? `${record.matchedCases.length} 条` : "-"
      },
      {
        title: "操作",
        width: 300,
        fixed: "right",
        render: (_, record) => (
          <Space wrap>
            <Button size="small" onClick={() => applyRiskAction(record, "CONFIRM")}>
              {"确认"}
            </Button>
            <Button size="small" onClick={() => generateSupplementCases(record)}>
              {"生成补充用例"}
            </Button>
            <Button size="small" onClick={() => applyRiskAction(record, "MARK_PASS")}>
              {"通过"}
            </Button>
            <Button size="small" danger onClick={() => applyRiskAction(record, "IGNORE")}>
              {"忽略"}
            </Button>
          </Space>
        )
      }
    ];

    return (
      <div className="stack">
        <Card
          className="panel-card"
          title="变更文件和风险覆盖分析"
          extra={
            <Button type="link" onClick={() => setCurrentStep("diff")}>
              {"返回任务与准入报告"}
            </Button>
          }
        >
          <Row gutter={[16, 16]}>
            <Col xs={24} md={8}>
              <Statistic title="任务编码" value={diffReport.task.taskCode || "-"} />
            </Col>
            <Col xs={12} md={4}>
              <Statistic title="变更文件" value={diffReport.report?.changedFileCount || diffReport.changedFiles.length} suffix="个" />
            </Col>
            <Col xs={12} md={4}>
              <Statistic title="高风险" value={diffReport.report?.highRiskCount || 0} suffix="项" />
            </Col>
            <Col xs={12} md={4}>
              <Statistic title="未覆盖" value={diffReport.report?.notCoveredRiskCount || 0} suffix="项" />
            </Col>
            <Col xs={12} md={4}>
              <Statistic title="准入" value={diffReport.report?.gateStatus || diffReport.task.mergeGateStatus || "-"} />
            </Col>
          </Row>
        </Card>

        {latestSupplementResult?.generatedCases?.length ? (
          <Card
            className="panel-card"
            title="补充用例输出"
            extra={
              <Button type="link" onClick={openSupplementDrafts}>
                查看草稿页
              </Button>
            }
          >
            <Alert
              showIcon
              type="success"
              message={`已生成 ${latestSupplementResult.generatedCases.length} 条补充用例草稿，数据保存在测试用例草稿表。`}
            />
            <List
              style={{ marginTop: 16 }}
              size="small"
              dataSource={latestSupplementResult.generatedCases}
              renderItem={(item) => (
                <List.Item>
                  <Space direction="vertical" size={0}>
                    <Text strong>{item.caseTitle || item.caseId}</Text>
                    <Text type="secondary">{`draftCaseId: ${item.draftCaseId} | caseId: ${item.caseId} | 优先级: ${item.priority}`}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        ) : null}

        <Card className="panel-card" title="变更文件">
          <Table<DiffChangedFileVO>
            rowKey="changedFileId"
            columns={changedFileColumns}
            dataSource={diffReport.changedFiles}
            pagination={{ pageSize: 6 }}
            scroll={{ x: 880 }}
          />
        </Card>

        <Card className="panel-card" title="风险与覆盖分析">
          <Table<DiffRiskItemVO>
            rowKey="riskId"
            columns={riskColumns}
            dataSource={diffReport.riskList}
            expandable={{
              expandedRowRender: (record) => (
                <div className="stack">
                  <Paragraph>{record.riskReason}</Paragraph>
                  <Text type="secondary">{record.coverageReason}</Text>
                  <List
                    size="small"
                    dataSource={record.matchedCases || []}
                    locale={{ emptyText: "暂无匹配用例" }}
                    renderItem={(item) => (
                      <List.Item>
                        <Space direction="vertical" size={0}>
                          <Text strong>{item.caseTitle || item.caseId}</Text>
                          <Text type="secondary">{item.judgementReason}</Text>
                        </Space>
                      </List.Item>
                    )}
                  />
                </div>
              )
            }}
            pagination={{ pageSize: 6, showTotal: (total) => `共 ${total} 条风险` }}
            scroll={{ x: 1220 }}
          />
        </Card>
      </div>
    );
  }
}

function validateFile(file: File) {
  if (!file.size) return "文件不能为空。";
  if (file.size > maxFileSize) return "文件超过 50MB 限制。";
  if (file.name.length > 180) return "文件名过长，请重命名后上传。";
  const extension = file.name.split(".").pop()?.toLowerCase() || "";
  if (!allowedExtensions.includes(extension)) return `文件格式不支持，请上传 ${allowedExtensions.join(" / ")}。`;
  return "";
}

function FileCard({ file }: { file: File }) {
  return (
    <Card size="small" title="文件卡片">
      <Descriptions column={1} size="small">
        <Descriptions.Item label="文件名">{file.name}</Descriptions.Item>
        <Descriptions.Item label="文件类型">{file.name.split(".").pop() || "-"}</Descriptions.Item>
        <Descriptions.Item label="文件大小">{formatSize(file.size)}</Descriptions.Item>
        <Descriptions.Item label="上传状态">
          <Tag color="blue">待上传</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="解析状态">
          <Tag>待解析</Tag>
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

function DocumentDescriptions({
  documentInfo,
  parseSummary
}: {
  documentInfo: DocumentVO;
  parseSummary: DocumentParseSummaryVO | null;
}) {
  return (
    <Descriptions bordered column={{ xs: 1, md: 2, xl: 4 }} size="small">
      <Descriptions.Item label="Document ID">{documentInfo.documentId}</Descriptions.Item>
      <Descriptions.Item label="文件名">{documentInfo.fileName || documentInfo.title}</Descriptions.Item>
      <Descriptions.Item label="文件类型">{documentInfo.fileType || documentInfo.sourceType}</Descriptions.Item>
      <Descriptions.Item label="解析状态">
        <StatusTag value={documentInfo.parseStatus || parseSummary?.parseStatus} />
      </Descriptions.Item>
      <Descriptions.Item label="文本长度">{documentInfo.rawTextLength || parseSummary?.rawTextLength || 0}</Descriptions.Item>
      <Descriptions.Item label="Chunk 数量">{parseSummary?.chunkCount || 0}</Descriptions.Item>
      <Descriptions.Item label="上传时间">{documentInfo.uploadedAt || documentInfo.createdAt || "-"}</Descriptions.Item>
      <Descriptions.Item label="解析时间">{documentInfo.parsedAt || "-"}</Descriptions.Item>
    </Descriptions>
  );
}

function SummaryItem({ label, value, status }: { label: string; value: string; status?: string }) {
  return (
    <div className="summary-item">
      <Text type="secondary">{label}</Text>
      <Space>
        {status ? <StatusTag value={status} /> : null}
        <Text strong>{value}</Text>
      </Space>
    </div>
  );
}

function StatusTag({ value }: { value?: string }) {
  const colorMap: Record<string, string> = {
    SUCCESS: "green",
    FAILED: "red",
    PROCESSING: "blue",
    PENDING: "gold",
    APPROVED: "green",
    REJECTED: "red"
  };
  const labelMap: Record<string, string> = {
    SUCCESS: "成功",
    FAILED: "失败",
    PROCESSING: "处理中",
    PENDING: "待处理",
    APPROVED: "已确认",
    REJECTED: "已驳回",
    RUNNING: "运行中",
    WAIT_CASE: "待补用例",
    WAIT_TEST: "待测试",
    PASSED: "已通过",
    BLOCKED: "阻塞",
    CLOSED: "已关闭",
    CONFIRMED: "已确认",
    IGNORED: "已忽略"
  };
  return <Tag color={value ? colorMap[value] || "default" : "default"}>{value ? labelMap[value] || value : "未开始"}</Tag>;
}
function RiskLevelTag({ value }: { value?: string }) {
  const colorMap: Record<string, string> = {
    HIGH: "red",
    MEDIUM: "orange",
    LOW: "green",
    P0: "red",
    P1: "orange",
    P2: "green"
  };
  return <Tag color={value ? colorMap[value] || "default" : "default"}>{value || "-"}</Tag>;
}

function CoverageTag({ value }: { value?: string }) {
  const colorMap: Record<string, string> = {
    COVERED: "green",
    PARTIAL_COVERED: "orange",
    NOT_COVERED: "red",
    NEED_CONFIRM: "blue"
  };
  const labelMap: Record<string, string> = {
    COVERED: "已覆盖",
    PARTIAL_COVERED: "部分覆盖",
    NOT_COVERED: "未覆盖",
    NEED_CONFIRM: "待确认"
  };
  return <Tag color={value ? colorMap[value] || "default" : "default"}>{value ? labelMap[value] || value : "-"}</Tag>;
}
function GateTag({ value }: { value?: string }) {
  const colorMap: Record<string, string> = {
    PASS: "green",
    WARNING: "orange",
    BLOCK: "red",
    MANUAL_REVIEW: "blue"
  };
  const labelMap: Record<string, string> = {
    PASS: "通过",
    WARNING: "警告",
    BLOCK: "阻塞",
    MANUAL_REVIEW: "人工复核"
  };
  return <Tag color={value ? colorMap[value] || "default" : "default"}>{value ? labelMap[value] || value : "-"}</Tag>;
}
function RiskTag({ value }: { value?: string }) {
  const color = value === "P0" ? "red" : value === "P1" ? "orange" : value === "P2" ? "green" : "default";
  const label = value === "P0" ? "P0" : value === "P1" ? "P1" : value === "P2" ? "P2" : (value || "-");
  return <Tag color={color}>{label}</Tag>;
}

function getStepStatus(
  step: StepKey,
  current: StepKey,
  documentInfo: DocumentVO | null,
  chunks: DocumentChunkVO[],
  drafts: TestCaseDraftVO[],
  cases: TestCaseVO[]
): StepStatus {
  if (step === current) return "process";
  if (step === "input") return documentInfo ? "finish" : "wait";
  if (step === "parse") return chunks.length ? "finish" : documentInfo ? "process" : "wait";
  if (step === "generate") return drafts.length ? "finish" : chunks.length ? "process" : "wait";
  if (step === "review") return cases.length ? "finish" : drafts.length ? "process" : "wait";
  if (step === "export") return cases.length ? "finish" : "wait";
  return "wait";
}

function loadingText(key: string | null) {
  const map: Record<string, string> = {
    "create-text": "正在创建文本文档",
    upload: "正在上传文件，请稍候",
    parse: "Apache Tika 正在解析文档内容",
    "refresh-document": "正在刷新解析结果",
    extract: "大模型正在分析业务规则",
    generate: "大模型正在生成测试用例草稿",
    "save-draft": "正在保存人工编辑结果",
    "approve-draft": "正在确认草稿",
    "batch-approve": "正在批量确认草稿"
  };
  return key ? map[key] || "正在处理" : "";
}

function formatJson(value?: string) {
  if (!value) return "{}";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function parseJsonArray(value?: string): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map((item) => (typeof item === "string" ? item : JSON.stringify(item))) : [];
  } catch {
    return [];
  }
}

function countJsonItems(value?: string) {
  return parseJsonArray(value).length;
}

function renderJsonTags(value?: string) {
  const items = parseJsonArray(value);
  if (!items.length) return <Text type="secondary">-</Text>;
  return (
    <Space size={[0, 4]} wrap>
      {items.slice(0, 3).map((item) => (
        <Tag key={item}>{item}</Tag>
      ))}
      {items.length > 3 ? <Tag>+{items.length - 3}</Tag> : null}
    </Space>
  );
}

function draftToForm(draft: TestCaseDraftVO) {
  const riskLevel = draft.riskLevel || "P1";
  const normalizedStepsJson = normalizeStepsJsonForEditor(draft.stepsJson || "[]");
  const normalizedExpectedResultsJson = normalizeExpectedResultsJsonForEditor(draft.stepsJson, draft.expectedResultsJson);
  return {
    title: draft.title,
    priority: draft.priority || "P1",
    caseType: draft.caseType ? formatCaseType(draft.caseType) : getDefaultCaseTypeForRiskLevel(riskLevel),
    riskLevel,
    preconditionsJson: normalizeArray(draft.preconditionsJson),
    stepsJson: normalizedStepsJson,
    expectedResultsJson: normalizedExpectedResultsJson,
    requirementRefsJson: normalizeArray(draft.requirementRefsJson),
    riskTagsJson: normalizeArray(draft.riskTagsJson)
  };
}

function normalizeStepsJsonForEditor(stepsJson?: string) {
  if (!stepsJson) return "[]";
  try {
    const parsed = JSON.parse(stepsJson);
    if (!Array.isArray(parsed)) return formatJson(stepsJson);
    const normalized = parsed.map((item) => {
      if (item && typeof item === "object" && !Array.isArray(item)) {
        const { expected_result: _ignored, ...rest } = item as Record<string, unknown>;
        return rest;
      }
      return item;
    });
    return JSON.stringify(normalized, null, 2);
  } catch {
    return formatJson(stepsJson);
  }
}

function normalizeExpectedResultsJsonForEditor(stepsJson?: string, expectedResultsJson?: string) {
  if (expectedResultsJson) {
    return normalizeArray(expectedResultsJson);
  }
  if (!stepsJson) return "[]";
  try {
    const parsed = JSON.parse(stepsJson);
    if (!Array.isArray(parsed)) return "[]";
    const expectedResults = parsed
      .map((item) => (item && typeof item === "object" && !Array.isArray(item) ? (item as Record<string, unknown>).expected_result : undefined))
      .filter((item): item is string => typeof item === "string" && item.trim().length > 0);
    return JSON.stringify(expectedResults, null, 2);
  } catch {
    return "[]";
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export default App;

