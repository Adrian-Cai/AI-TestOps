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
