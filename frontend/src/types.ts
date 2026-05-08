export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface DocumentVO {
  documentId: string;
  title: string;
  sourceType: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  fileHash?: string;
  duplicateDocumentId?: string;
  parseStatus?: string;
  parseError?: string;
  rawTextSummary?: string;
  rawTextLength?: number;
  metadataJson?: string;
  uploadedAt?: string;
  parsedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DocumentParseSummaryVO {
  documentId: string;
  parseStatus: string;
  rawTextLength: number;
  chunkCount: number;
  metadataJson?: string;
}

export interface DocumentChunkVO {
  chunkId: string;
  documentId: string;
  chunkIndex: number;
  sectionTitle?: string;
  chunkText?: string;
  tokenCount?: number;
  extraJson?: string;
  createdAt?: string;
}

export interface RequirementExtractVO {
  requirementExtractId: string;
  generationId: string;
  documentId: string;
  requirementsJson?: string;
  businessRulesJson?: string;
  apiListJson?: string;
  fieldConstraintsJson?: string;
  exceptionCasesJson?: string;
  risksJson?: string;
  rawOutputJson?: string;
  createdAt?: string;
}

export interface GenerationRecordVO {
  generationId: string;
  documentId?: string;
  requirementExtractId?: string;
  promptTemplateCode?: string;
  modelCode?: string;
  modelName?: string;
  generationType?: string;
  inputSnapshotJson?: string;
  outputJson?: string;
  status?: string;
  errorMessage?: string;
  tokenInput?: number;
  tokenOutput?: number;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ValidationResultVO {
  validationId: string;
  generationId: string;
  validationType: string;
  status: string;
  errorDetailJson?: string;
  warningDetailJson?: string;
  createdAt?: string;
}

export interface TestCaseDraftVO {
  draftCaseId: string;
  caseId: string;
  generationId: string;
  documentId: string;
  requirementExtractId?: string;
  title: string;
  preconditionsJson?: string;
  stepsJson?: string;
  expectedResultsJson?: string;
  priority?: string;
  caseType?: string;
  riskLevel?: string;
  requirementRefsJson?: string;
  riskTagsJson?: string;
  reviewStatus?: string;
  rawCaseJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestCaseGenerateVO {
  generationId: string;
  documentId: string;
  requirementExtractId?: string;
  validationStatus?: string;
  draftCount: number;
  drafts: TestCaseDraftVO[];
}

export interface TestCaseVO {
  testCaseId: string;
  sourceDraftCaseId?: string;
  caseId: string;
  generationId: string;
  documentId: string;
  requirementExtractId?: string;
  title: string;
  preconditionsJson?: string;
  stepsJson?: string;
  expectedResultsJson?: string;
  priority?: string;
  caseType?: string;
  riskLevel?: string;
  requirementRefsJson?: string;
  riskTagsJson?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OperationLog {
  id: number;
  time: string;
  action: string;
  status: "success" | "error" | "info";
  detail?: string;
}

export interface DiffAnalysisTaskVO {
  taskId: number;
  taskCode: string;
  documentId?: string;
  requirementExtractId?: string;
  repoUrl: string;
  repoName?: string;
  sourceBranch: string;
  targetBranch: string;
  status: string;
  failReason?: string;
  changedFileCount: number;
  highRiskCount: number;
  mediumRiskCount: number;
  lowRiskCount: number;
  notCoveredRiskCount: number;
  mergeGateStatus?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DiffChangedFileVO {
  changedFileId: number;
  oldFilePath?: string;
  newFilePath: string;
  changeType: string;
  language?: string;
  fileRole?: string;
  additions: number;
  deletions: number;
  changes: number;
  patch?: string;
  patchSummary?: string;
  testFile?: boolean;
  initialRiskLevel?: string;
  initialRiskReason?: string;
}

export interface DiffRiskCaseRelVO {
  id: number;
  caseDbId?: number;
  testCaseId?: string;
  caseId?: string;
  caseTitle?: string;
  coverageJudgement?: string;
  judgementReason?: string;
  similarityScore?: number;
}

export interface DiffRiskItemVO {
  riskId: number;
  riskCode: string;
  riskTitle: string;
  riskLevel: string;
  riskCategory: string;
  sourceType: string;
  affectedModule?: string;
  affectedScenarios?: string;
  riskReason?: string;
  testSuggestion?: string;
  missingTestScenarios?: string;
  coverageStatus: string;
  coverageReason?: string;
  processStatus: string;
  mergeGateImpact?: string;
  matchedCases?: DiffRiskCaseRelVO[];
}

export interface DiffMergeGateReportVO {
  reportId: number;
  taskId: number;
  reportCode: string;
  gateStatus: string;
  gateReason?: string;
  changedFileCount: number;
  changedMethodCount: number;
  highRiskCount: number;
  mediumRiskCount: number;
  lowRiskCount: number;
  coveredRiskCount: number;
  partialCoveredRiskCount: number;
  notCoveredRiskCount: number;
  needConfirmRiskCount: number;
  blockedRiskCount: number;
  suggestedCaseCount: number;
  suggestedRegressionModules?: string;
  reportSummary?: string;
  createdAt?: string;
}

export interface DiffAnalysisReportVO {
  task: DiffAnalysisTaskVO;
  report?: DiffMergeGateReportVO;
  changedFiles: DiffChangedFileVO[];
  riskList: DiffRiskItemVO[];
}

export interface DiffSupplementCaseVO {
  riskId: number;
  generatedCases: Array<{
    draftCaseId: string;
    caseId: string;
    caseTitle: string;
    priority: string;
    preconditionsJson?: string;
    stepsJson?: string;
    expectedResultsJson?: string;
  }>;
}
