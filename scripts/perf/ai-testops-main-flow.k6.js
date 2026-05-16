import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const FLOW = __ENV.FLOW || 'upload_parse';
const RATE = Number(__ENV.RATE || '2');
const DURATION = __ENV.DURATION || '5m';
const PRE_ALLOCATED_VUS = Number(__ENV.PREALLOCATED_VUS || Math.max(10, RATE * 4));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(30, RATE * 8));
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || '1');

const uploadFilePath = __ENV.UPLOAD_FILE || './fixtures/upload-sample.md';
const uploadFileName = __ENV.UPLOAD_FILE_NAME || 'upload-sample.md';
const uploadFile = open(uploadFilePath, 'b');

const draftGroupsPath = __ENV.DRAFT_GROUPS || './draft-groups.example.json';
const draftGroups = new SharedArray('draft-groups', () => JSON.parse(open(draftGroupsPath)));

export const options = {
  scenarios: {
    [FLOW]: {
      executor: 'constant-arrival-rate',
      exec: FLOW === 'approve_export' ? 'approveExportFlow' : 'uploadParseFlow',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2500', 'p(99)<5000'],
    business_success_rate: ['rate>0.99'],
    upload_duration: ['avg<500', 'p(95)<1000', 'p(99)<2000'],
    parse_duration: ['avg<1000', 'p(95)<2500', 'p(99)<5000'],
    approve_duration: ['avg<500', 'p(95)<1200', 'p(99)<2500'],
    export_json_duration: ['avg<600', 'p(95)<1200', 'p(99)<2500'],
    export_excel_duration: ['avg<1200', 'p(95)<2500', 'p(99)<5000'],
  },
};

const businessSuccessRate = new Rate('business_success_rate');
const uploadDuration = new Trend('upload_duration', true);
const parseDuration = new Trend('parse_duration', true);
const approveDuration = new Trend('approve_duration', true);
const exportJsonDuration = new Trend('export_json_duration', true);
const exportExcelDuration = new Trend('export_excel_duration', true);

export function uploadParseFlow() {
  const iteration = exec.scenario.iterationInTest;
  const title = `perf-upload-${Date.now()}-${iteration}`;

  const uploadRes = http.post(
    `${BASE_URL}/api/ai-testops/documents/upload`,
    {
      title,
      file: http.file(uploadFile, uploadFileName),
    },
    { tags: { endpoint: 'upload_document' } }
  );
  uploadDuration.add(uploadRes.timings.duration);

  const uploadData = apiData(uploadRes, 'upload_document');
  const documentId = uploadData && uploadData.documentId;
  check(uploadRes, {
    'upload returns documentId': () => Boolean(documentId),
  });

  if (!documentId) {
    businessSuccessRate.add(false);
    return;
  }

  const parseRes = http.post(
    `${BASE_URL}/api/ai-testops/documents/${encodeURIComponent(documentId)}/parse`,
    null,
    { tags: { endpoint: 'parse_document' } }
  );
  parseDuration.add(parseRes.timings.duration);
  const parseOk = apiOk(parseRes, 'parse_document');
  businessSuccessRate.add(parseOk);

  sleep(THINK_TIME_SECONDS);
}

export function approveExportFlow() {
  const iteration = exec.scenario.iterationInTest;
  if (iteration >= draftGroups.length && __ENV.ALLOW_DRAFT_REUSE !== 'true') {
    throw new Error(
      `Not enough draft groups. Need at least rate*duration groups, but only ${draftGroups.length} were provided.`
    );
  }

  const group = draftGroups[iteration % draftGroups.length];
  const draftCaseIds = group.draftCaseIds || [];
  const documentId = group.documentId;
  const reviewer = group.reviewer || 'perf_user';

  const approveRes = http.post(
    `${BASE_URL}/api/ai-testops/testcases/drafts/batch-approve`,
    JSON.stringify({ draftCaseIds, reviewer }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'batch_approve' },
    }
  );
  approveDuration.add(approveRes.timings.duration);
  const approveOk = apiOk(approveRes, 'batch_approve');

  let exportOk = approveOk;
  if (documentId && (__ENV.EXPORT_FORMATS || 'json,excel').includes('json')) {
    const jsonRes = http.get(
      `${BASE_URL}/api/ai-testops/export/testcases/json?documentId=${encodeURIComponent(documentId)}`,
      { tags: { endpoint: 'export_json' } }
    );
    exportJsonDuration.add(jsonRes.timings.duration);
    exportOk = exportOk && check(jsonRes, { 'json export status 200': (res) => res.status === 200 });
  }

  if (documentId && (__ENV.EXPORT_FORMATS || 'json,excel').includes('excel')) {
    const excelRes = http.get(
      `${BASE_URL}/api/ai-testops/export/testcases/excel?documentId=${encodeURIComponent(documentId)}`,
      { tags: { endpoint: 'export_excel' } }
    );
    exportExcelDuration.add(excelRes.timings.duration);
    exportOk = exportOk && check(excelRes, { 'excel export status 200': (res) => res.status === 200 });
  }

  businessSuccessRate.add(exportOk);
  sleep(THINK_TIME_SECONDS);
}

function apiOk(res, stepName) {
  const ok = res.status === 200 && jsonValue(res, 'code') === 0;
  check(res, { [`${stepName} code is success`]: () => ok });
  return ok;
}

function apiData(res, stepName) {
  if (!apiOk(res, stepName)) {
    return null;
  }
  return jsonValue(res, 'data');
}

function jsonValue(res, selector) {
  try {
    return res.json(selector);
  } catch (error) {
    return undefined;
  }
}
