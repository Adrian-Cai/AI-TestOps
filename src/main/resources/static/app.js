const api = {
  createText: "/api/ai-testops/documents/text",
  upload: "/api/ai-testops/documents/upload",
  document: (id) => `/api/ai-testops/documents/${encodeURIComponent(id)}`,
  parse: (id) => `/api/ai-testops/documents/${encodeURIComponent(id)}/parse`,
  chunks: (id) => `/api/ai-testops/documents/${encodeURIComponent(id)}/chunks`,
  extractRequirements: "/api/ai-testops/ai/requirements/extract",
  generation: (id) => `/api/ai-testops/generations/${encodeURIComponent(id)}`,
  validations: (id) => `/api/ai-testops/validations/${encodeURIComponent(id)}`,
  generateCases: "/api/ai-testops/testcases/generate",
  drafts: (query) => `/api/ai-testops/testcases/drafts${query ? `?${query}` : ""}`,
  draft: (id) => `/api/ai-testops/testcases/drafts/${encodeURIComponent(id)}`,
  approveDraft: (id) => `/api/ai-testops/testcases/drafts/${encodeURIComponent(id)}/approve`,
  rejectDraft: (id) => `/api/ai-testops/testcases/drafts/${encodeURIComponent(id)}/reject`,
  cases: (query) => `/api/ai-testops/testcases${query ? `?${query}` : ""}`,
  reviews: (id) => `/api/ai-testops/reviews/${encodeURIComponent(id)}`,
  exportJson: (query) => `/api/ai-testops/export/testcases/json${query ? `?${query}` : ""}`,
  exportExcel: (query) => `/api/ai-testops/export/testcases/excel${query ? `?${query}` : ""}`
};

const resultOutput = document.querySelector("#resultOutput");
const chunkList = document.querySelector("#chunkList");
const documentIdInput = document.querySelector("#documentId");
const generationIdInput = document.querySelector("#generationId");
const draftCaseIdInput = document.querySelector("#draftCaseId");
const caseLookupIdInput = document.querySelector("#caseLookupId");
const draftTitleEditInput = document.querySelector("#draftTitleEdit");

function setBusy(formOrButton, busy) {
  const controls = formOrButton.querySelectorAll
    ? formOrButton.querySelectorAll("button, input, textarea")
    : [formOrButton];
  controls.forEach((control) => {
    control.disabled = busy;
  });
}

function showResult(value) {
  resultOutput.classList.remove("error");
  resultOutput.textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function showError(error) {
  resultOutput.classList.add("error");
  resultOutput.textContent = error.message || String(error);
}

function getDocumentId() {
  const documentId = documentIdInput.value.trim();
  if (!documentId) {
    throw new Error("请先输入或创建一个 Document ID。");
  }
  return documentId;
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(payload?.message || `HTTP ${response.status}`);
  }
  if (!payload || payload.code !== 0) {
    throw new Error(payload?.message || "接口返回失败");
  }
  return payload.data;
}

function rememberDocument(document) {
  if (document?.documentId) {
    documentIdInput.value = document.documentId;
  }
}

function rememberGeneration(result) {
  if (result?.generationId) {
    generationIdInput.value = result.generationId;
  }
}

function rememberDraft(result) {
  const draft = result?.drafts?.[0] || result;
  if (draft?.draftCaseId) {
    draftCaseIdInput.value = draft.draftCaseId;
    caseLookupIdInput.value = draft.draftCaseId;
  }
  if (draft?.title) {
    draftTitleEditInput.value = draft.title;
  }
}

function getGenerationId() {
  const generationId = generationIdInput.value.trim();
  if (!generationId) {
    throw new Error("请先输入或生成一个 Generation ID。");
  }
  return generationId;
}

function getDraftCaseId() {
  const draftCaseId = draftCaseIdInput.value.trim();
  if (!draftCaseId) {
    throw new Error("请先输入或生成一个 Draft Case ID。");
  }
  return draftCaseId;
}

document.querySelector("#textForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  setBusy(form, true);
  try {
    const data = await requestJson(api.createText, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: document.querySelector("#textTitle").value.trim(),
        content: document.querySelector("#textContent").value.trim()
      })
    });
    rememberDocument(data);
    showResult(data);
    chunkList.innerHTML = '<p class="empty">文档已创建，点击“解析文档”生成 chunks。</p>';
  } catch (error) {
    showError(error);
  } finally {
    setBusy(form, false);
  }
});

document.querySelector("#uploadForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const formData = new FormData();
  const file = document.querySelector("#uploadFile").files[0];
  const title = document.querySelector("#uploadTitle").value.trim();
  formData.append("file", file);
  if (title) {
    formData.append("title", title);
  }

  setBusy(form, true);
  try {
    const data = await requestJson(api.upload, {
      method: "POST",
      body: formData
    });
    rememberDocument(data);
    showResult(data);
    chunkList.innerHTML = '<p class="empty">文件已上传，点击“解析文档”提取文本和 chunks。</p>';
  } catch (error) {
    showError(error);
  } finally {
    setBusy(form, false);
  }
});

document.querySelector("#parseBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.parse(getDocumentId()), { method: "POST" });
    showResult(data);
    await loadChunks();
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#detailBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.document(getDocumentId()));
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#chunksBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    await loadChunks();
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#extractBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.extractRequirements, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        documentId: getDocumentId(),
        modelCode: "default",
        promptTemplateCode: "REQUIREMENT_EXTRACT"
      })
    });
    rememberGeneration(data);
    caseLookupIdInput.value = data.requirementExtractId || "";
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#generationBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.generation(getGenerationId()));
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#generateCasesBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.generateCases, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        documentId: getDocumentId(),
        modelCode: "default",
        promptTemplateCode: "TEST_CASE_GENERATE"
      })
    });
    rememberGeneration(data);
    rememberDraft(data);
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#validationBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.validations(getGenerationId()));
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#draftsBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const query = new URLSearchParams();
    if (documentIdInput.value.trim()) {
      query.set("documentId", documentIdInput.value.trim());
    }
    if (generationIdInput.value.trim()) {
      query.set("generationId", generationIdInput.value.trim());
    }
    const data = await requestJson(api.drafts(query.toString()));
    rememberDraft(data[0]);
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#approveBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.approveDraft(getDraftCaseId()), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: "页面确认", reviewer: "manual_user" })
    });
    caseLookupIdInput.value = data.testCaseId || getDraftCaseId();
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#editDraftBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const title = draftTitleEditInput.value.trim();
    if (!title) {
      throw new Error("请输入要保存的草稿标题。");
    }
    const data = await requestJson(api.draft(getDraftCaseId()), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title, reviewer: "manual_user" })
    });
    rememberDraft(data);
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#rejectBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const data = await requestJson(api.rejectDraft(getDraftCaseId()), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: "页面驳回", reviewer: "manual_user" })
    });
    rememberDraft(data);
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#casesBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const query = new URLSearchParams();
    if (documentIdInput.value.trim()) {
      query.set("documentId", documentIdInput.value.trim());
    }
    const data = await requestJson(api.cases(query.toString()));
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#reviewsBtn").addEventListener("click", async (event) => {
  setBusy(event.currentTarget, true);
  try {
    const lookupId = caseLookupIdInput.value.trim() || getDraftCaseId();
    const data = await requestJson(api.reviews(lookupId));
    showResult(data);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(event.currentTarget, false);
  }
});

document.querySelector("#exportJsonBtn").addEventListener("click", () => {
  window.open(api.exportJson(buildDocumentQuery()), "_blank");
});

document.querySelector("#exportExcelBtn").addEventListener("click", () => {
  window.open(api.exportExcel(buildDocumentQuery()), "_blank");
});

async function loadChunks() {
  const data = await requestJson(api.chunks(getDocumentId()));
  showResult({ chunkCount: data.length, chunks: data });
  renderChunks(data);
}

function renderChunks(chunks) {
  if (!chunks.length) {
    chunkList.innerHTML = '<p class="empty">没有 chunk 数据。请先解析文档。</p>';
    return;
  }

  chunkList.innerHTML = chunks.map((chunk) => `
    <article class="chunk-card">
      <header>
        <span>${escapeHtml(chunk.chunkId)}</span>
        <span>#${chunk.chunkIndex} · ${chunk.tokenCount || 0} tokens</span>
      </header>
      <p>${escapeHtml(chunk.chunkText || "").slice(0, 700)}</p>
    </article>
  `).join("");
}

function buildDocumentQuery() {
  const query = new URLSearchParams();
  if (documentIdInput.value.trim()) {
    query.set("documentId", documentIdInput.value.trim());
  }
  return query.toString();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
