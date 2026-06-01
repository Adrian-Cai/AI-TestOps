import type {
  ApiResponse,
  DiffAnalysisReportVO,
  DiffAnalysisSourceVO,
  DiffAnalysisTaskCreateRequest,
  DiffAnalysisTaskVO,
  DiffSupplementCaseVO,
  DocumentChunkVO,
  DocumentParseSummaryVO,
  DocumentVO,
  GenerationRecordVO,
  RequirementExtractVO,
  TestCaseDraftVO,
  TestCaseGenerateVO,
  TestCaseVO,
  ValidationResultVO,
} from './types';

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  if (!response.ok) {
    throw new Error(payload?.message || `HTTP ${response.status}`);
  }
  if (!payload || payload.code !== 0) {
    throw new Error(payload?.message || '接口返回失败');
  }
  return payload.data;
}

export const api = {
  createTextDocument(input: { title: string; content: string }) {
    return requestJson<DocumentVO>('/api/ai-testops/documents/text', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  },
  uploadDocument(input: { title?: string; file: File }) {
    const body = new FormData();
    body.append('file', input.file);
    if (input.title) {
      body.append('title', input.title);
    }
    return requestJson<DocumentVO>('/api/ai-testops/documents/upload', {
      method: 'POST',
      body,
    });
  },
  parseDocument(documentId: string) {
    return requestJson<DocumentParseSummaryVO>(
      `/api/ai-testops/documents/${encodeURIComponent(documentId)}/parse`,
      {
        method: 'POST',
      },
    );
  },
  getDocument(documentId: string) {
    return requestJson<DocumentVO>(`/api/ai-testops/documents/${encodeURIComponent(documentId)}`);
  },
  listChunks(documentId: string) {
    return requestJson<DocumentChunkVO[]>(
      `/api/ai-testops/documents/${encodeURIComponent(documentId)}/chunks`,
    );
  },
  extractRequirements(input: {
    documentId: string;
    modelCode: string;
    promptTemplateCode: string;
  }) {
    return requestJson<RequirementExtractVO>('/api/ai-testops/ai/requirements/extract', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  },
  getGeneration(generationId: string) {
    return requestJson<GenerationRecordVO>(
      `/api/ai-testops/generations/${encodeURIComponent(generationId)}`,
    );
  },
  listValidations(generationId: string) {
    return requestJson<ValidationResultVO[]>(
      `/api/ai-testops/validations/${encodeURIComponent(generationId)}`,
    );
  },
  generateCases(input: {
    documentId: string;
    requirementExtractId?: string;
    modelCode: string;
    promptTemplateCode: string;
    caseCount?: number;
  }) {
    return requestJson<TestCaseGenerateVO>('/api/ai-testops/testcases/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  },
  listDrafts(input: { documentId?: string; generationId?: string }) {
    const query = new URLSearchParams();
    if (input.documentId) query.set('documentId', input.documentId);
    if (input.generationId) query.set('generationId', input.generationId);
    const suffix = query.toString() ? `?${query}` : '';
    return requestJson<TestCaseDraftVO[]>(`/api/ai-testops/testcases/drafts${suffix}`);
  },
  updateDraft(draftCaseId: string, input: Record<string, string>) {
    return requestJson<TestCaseDraftVO>(
      `/api/ai-testops/testcases/drafts/${encodeURIComponent(draftCaseId)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      },
    );
  },
  approveDraft(draftCaseId: string) {
    return requestJson<TestCaseVO>(
      `/api/ai-testops/testcases/drafts/${encodeURIComponent(draftCaseId)}/approve`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: '页面确认', reviewer: 'manual_user' }),
      },
    );
  },
  rejectDraft(draftCaseId: string) {
    return requestJson<TestCaseDraftVO>(
      `/api/ai-testops/testcases/drafts/${encodeURIComponent(draftCaseId)}/reject`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: '页面驳回', reviewer: 'manual_user' }),
      },
    );
  },
  batchApprove(draftCaseIds: string[]) {
    return requestJson<TestCaseVO[]>('/api/ai-testops/testcases/drafts/batch-approve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ draftCaseIds, reviewer: 'manual_user' }),
    });
  },
  listCases(input: { documentId?: string; requirementExtractId?: string }) {
    const query = new URLSearchParams();
    if (input.documentId) query.set('documentId', input.documentId);
    if (input.requirementExtractId) query.set('requirementExtractId', input.requirementExtractId);
    const suffix = query.toString() ? `?${query}` : '';
    return requestJson<TestCaseVO[]>(`/api/ai-testops/testcases${suffix}`);
  },
  exportJsonUrl(documentId?: string) {
    const query = new URLSearchParams();
    if (documentId) query.set('documentId', documentId);
    return `/api/ai-testops/export/testcases/json${query.toString() ? `?${query}` : ''}`;
  },
  exportExcelUrl(documentId?: string) {
    const query = new URLSearchParams();
    if (documentId) query.set('documentId', documentId);
    return `/api/ai-testops/export/testcases/excel${query.toString() ? `?${query}` : ''}`;
  },
  createDiffTask(input: DiffAnalysisTaskCreateRequest) {
    return requestJson<DiffAnalysisTaskVO>('/api/ai-testops/diff-analysis/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  },
  listDiffTasks(input: {
    documentId?: string;
    requirementExtractId?: string;
    status?: string;
    sourceBranch?: string;
    targetBranch?: string;
  }) {
    const query = new URLSearchParams();
    if (input.documentId) query.set('documentId', input.documentId);
    if (input.requirementExtractId) query.set('requirementExtractId', input.requirementExtractId);
    if (input.status) query.set('status', input.status);
    if (input.sourceBranch) query.set('sourceBranch', input.sourceBranch);
    if (input.targetBranch) query.set('targetBranch', input.targetBranch);
    const suffix = query.toString() ? `?${query}` : '';
    return requestJson<DiffAnalysisTaskVO[]>(`/api/ai-testops/diff-analysis/tasks${suffix}`);
  },
  listDiffSources(limit?: number) {
    const query = new URLSearchParams();
    if (limit) query.set('limit', String(limit));
    const suffix = query.toString() ? `?${query}` : '';
    return requestJson<DiffAnalysisSourceVO[]>(
      `/api/ai-testops/diff-analysis/sources/recent${suffix}`,
    );
  },
  listRepositoryBranches(repoUrl: string) {
    const query = new URLSearchParams({ repoUrl });
    return requestJson<string[]>(`/api/ai-testops/diff-analysis/repositories/branches?${query}`);
  },
  getDiffReport(taskId: number) {
    return requestJson<DiffAnalysisReportVO>(
      `/api/ai-testops/diff-analysis/tasks/${encodeURIComponent(taskId)}/report`,
    );
  },
  applyDiffRiskAction(
    riskId: number,
    input: {
      actionType: string;
      actionDesc?: string;
      operator: string;
      ignoreReason?: string;
      relatedCaseIds?: number[];
    },
  ) {
    return requestJson<void>(
      `/api/ai-testops/diff-analysis/risks/${encodeURIComponent(riskId)}/actions`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      },
    ).then(() => true);
  },
  generateDiffSupplementCases(riskId: number) {
    return requestJson<DiffSupplementCaseVO>(
      `/api/ai-testops/diff-analysis/risks/${encodeURIComponent(riskId)}/generate-supplement-cases`,
      { method: 'POST' },
    );
  },
};
