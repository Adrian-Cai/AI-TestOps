package com.example.aitestops.testcase;

import com.example.aitestops.ai.dto.RequirementExtractRequest;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.ai.service.AiTestopsValidationResultService;
import com.example.aitestops.ai.vo.RequirementExtractVO;
import com.example.aitestops.ai.vo.ValidationResultVO;
import com.example.aitestops.common.enums.ReviewStatusEnum;
import com.example.aitestops.common.enums.ValidationStatusEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.document.dto.TextDocumentCreateRequest;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.document.vo.DocumentVO;
import com.example.aitestops.review.service.AiTestopsReviewRecordService;
import com.example.aitestops.review.vo.ReviewRecordVO;
import com.example.aitestops.testcase.dto.TestCaseDraftBatchApproveRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftReviewRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftUpdateRequest;
import com.example.aitestops.testcase.dto.TestCaseGenerateRequest;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.example.aitestops.testcase.service.AiTestopsTestCaseDraftService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseDraftVO;
import com.example.aitestops.testcase.vo.TestCaseGenerateVO;
import com.example.aitestops.testcase.vo.TestCaseVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiTestopsTestCaseDraftServiceIntegrationTest {

    @Autowired
    private AiTestopsDocumentService documentService;

    @Autowired
    private AiTestopsRequirementExtractService requirementExtractService;

    @Autowired
    private AiTestopsTestCaseDraftService testCaseDraftService;

    @Autowired
    private AiTestopsValidationResultService validationResultService;

    @Autowired
    private AiTestopsTestCaseService testCaseService;

    @Autowired
    private AiTestopsReviewRecordService reviewRecordService;

    @Test
    void generateDraftsShouldValidateAiOutputAndPersistDraftCasesInMockMode() {
        TextDocumentCreateRequest createRequest = new TextDocumentCreateRequest();
        createRequest.setTitle("订单需求");
        createRequest.setContent("用户可以提交订单。\n\n库存不足时提交失败。");
        DocumentVO document = documentService.createTextDocument(createRequest);
        documentService.parseDocument(document.getDocumentId());

        RequirementExtractRequest extractRequest = new RequirementExtractRequest();
        extractRequest.setDocumentId(document.getDocumentId());
        RequirementExtractVO extract = requirementExtractService.extractRequirements(extractRequest);

        TestCaseGenerateRequest generateRequest = new TestCaseGenerateRequest();
        generateRequest.setDocumentId(document.getDocumentId());
        generateRequest.setRequirementExtractId(extract.getRequirementExtractId());
        TestCaseGenerateVO generated = testCaseDraftService.generateDrafts(generateRequest);

        List<ValidationResultVO> validations = validationResultService.listByGenerationId(generated.getGenerationId());
        List<TestCaseDraftVO> drafts = testCaseDraftService.listDrafts(document.getDocumentId(), generated.getGenerationId(), null);

        assertThat(generated.getGenerationId()).startsWith("GEN_");
        assertThat(generated.getDraftCount()).isEqualTo(1);
        assertThat(generated.getValidationStatus()).isEqualTo(ValidationStatusEnum.PASSED.name());
        assertThat(validations).hasSize(4);
        assertThat(validations).allMatch(item -> ValidationStatusEnum.PASSED.name().equals(item.getStatus()));
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getTitle()).isEqualTo("正常提交订单");
        assertThat(drafts.get(0).getReviewStatus()).isEqualTo("PENDING");
        assertThat(drafts.get(0).getStepsJson()).doesNotContain("expected_result");
        assertThat(drafts.get(0).getExpectedResultsJson()).isEqualTo("[\"页面正常展示\",\"订单提交成功\"]");
    }

    @Test
    void updateApproveAndRejectDraftsShouldPersistFormalCasesAndReviewRecords() {
        TestCaseGenerateVO generated = generateOneDraft();
        String draftCaseId = generated.getDrafts().get(0).getDraftCaseId();

        TestCaseDraftUpdateRequest updateRequest = new TestCaseDraftUpdateRequest();
        updateRequest.setTitle("人工调整后的正常提交订单");
        updateRequest.setReviewer("qa");
        TestCaseDraftVO updated = testCaseDraftService.updateDraft(draftCaseId, updateRequest);

        TestCaseDraftReviewRequest approveRequest = new TestCaseDraftReviewRequest();
        approveRequest.setReason("用例内容有效");
        approveRequest.setReviewer("qa");
        TestCaseVO approved = testCaseDraftService.approveDraft(draftCaseId, approveRequest);

        List<TestCaseVO> formalCases = testCaseService.listCases(generated.getDocumentId(), generated.getRequirementExtractId());
        List<ReviewRecordVO> reviews = reviewRecordService.listByCaseId(draftCaseId);

        assertThat(updated.getTitle()).isEqualTo("人工调整后的正常提交订单");
        assertThat(approved.getTestCaseId()).startsWith("TCDB_");
        assertThat(approved.getTitle()).isEqualTo("人工调整后的正常提交订单");
        assertThat(approved.getExpectedResultsJson()).isEqualTo("[\"页面正常展示\",\"订单提交成功\"]");
        assertThat(formalCases).hasSize(1);
        assertThat(reviews).extracting(ReviewRecordVO::getAction).contains("EDIT", "APPROVE");
        assertThatThrownBy(() -> testCaseDraftService.approveDraft(draftCaseId, approveRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");

        TestCaseGenerateVO rejectGenerated = generateOneDraft();
        String rejectDraftId = rejectGenerated.getDrafts().get(0).getDraftCaseId();
        TestCaseDraftReviewRequest rejectRequest = new TestCaseDraftReviewRequest();
        rejectRequest.setReason("场景重复");
        rejectRequest.setReviewer("qa");
        TestCaseDraftVO rejected = testCaseDraftService.rejectDraft(rejectDraftId, rejectRequest);

        assertThat(rejected.getReviewStatus()).isEqualTo("REJECTED");
        assertThat(reviewRecordService.listByCaseId(rejectDraftId))
                .extracting(ReviewRecordVO::getAction)
                .contains("REJECT");
    }

    @Test
    void approveDraftsShouldDisambiguateDuplicateAiCaseIdsForFormalCases() {
        TestCaseGenerateVO firstGenerated = generateOneDraft();
        String firstDraftCaseId = firstGenerated.getDrafts().get(0).getDraftCaseId();
        TestCaseDraftReviewRequest approveRequest = new TestCaseDraftReviewRequest();
        approveRequest.setReason("duplicate case id compatibility");
        approveRequest.setReviewer("qa");
        TestCaseVO firstApproved = testCaseDraftService.approveDraft(firstDraftCaseId, approveRequest);

        TestCaseGenerateVO secondGenerated = generateOneDraft();
        String secondDraftCaseId = secondGenerated.getDrafts().get(0).getDraftCaseId();
        TestCaseVO secondApproved = testCaseDraftService.approveDraft(secondDraftCaseId, approveRequest);

        String originalCaseId = secondGenerated.getDrafts().get(0).getCaseId();
        assertThat(firstApproved.getTestCaseId()).isNotEqualTo(secondApproved.getTestCaseId());
        assertThat(secondApproved.getCaseId()).startsWith(originalCaseId + "_");
        assertThat(secondApproved.getCaseId()).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void listDraftsShouldFallbackExpectedResultsFromLegacyStepsJson() {
        AiTestopsTestCaseDraft legacyDraft = new AiTestopsTestCaseDraft();
        legacyDraft.setDraftCaseId("DRAFT_LEGACY_001");
        legacyDraft.setCaseId("TC_LEGACY_001");
        legacyDraft.setGenerationId("GEN_LEGACY_001");
        legacyDraft.setDocumentId("DOC_LEGACY_001");
        legacyDraft.setTitle("历史草稿");
        legacyDraft.setPreconditionsJson("[]");
        legacyDraft.setStepsJson("""
                [
                  {"step_no":1,"action":"提交申请","expected_result":"提交成功"}
                ]
                """);
        legacyDraft.setPriority("P1");
        legacyDraft.setRequirementRefsJson("[\"REQ_001\"]");
        legacyDraft.setReviewStatus("PENDING");
        legacyDraft.setRawCaseJson("{}");
        legacyDraft.setCreatedAt(LocalDateTime.now());
        legacyDraft.setUpdatedAt(LocalDateTime.now());
        testCaseDraftService.save(legacyDraft);

        TestCaseDraftVO loaded = testCaseDraftService.getDraft("DRAFT_LEGACY_001");

        assertThat(loaded.getStepsJson()).isEqualTo("[{\"step_no\":1,\"action\":\"提交申请\"}]");
        assertThat(loaded.getExpectedResultsJson()).isEqualTo("[\"提交成功\"]");
    }

    @Test
    void approveDraftShouldAllowOverallExpectedResultsNotMatchingStepCount() {
        AiTestopsTestCaseDraft draft = new AiTestopsTestCaseDraft();
        draft.setDraftCaseId("DRAFT_MISMATCH_001");
        draft.setCaseId("TC_MISMATCH_001");
        draft.setGenerationId("GEN_MISMATCH_001");
        draft.setDocumentId("DOC_MISMATCH_001");
        draft.setTitle("鏁翠綋棰勬湡缁撴灉鍏煎");
        draft.setPreconditionsJson("[]");
        draft.setStepsJson("""
                [
                  {"step_no":1,"action":"鎵撳紑鐢宠椤甸潰"},
                  {"step_no":2,"action":"鎻愪氦宸梾鐢宠"}
                ]
                """);
        draft.setExpectedResultsJson("[\"鐢宠鎻愪氦鎴愬姛锛岀郴缁熻繑鍥炵敵璇峰崟 ID\"]");
        draft.setPriority("P1");
        draft.setRequirementRefsJson("[\"REQ_001\"]");
        draft.setReviewStatus("PENDING");
        draft.setRawCaseJson("{}");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        testCaseDraftService.save(draft);

        TestCaseDraftReviewRequest approveRequest = new TestCaseDraftReviewRequest();
        approveRequest.setReviewer("qa");
        approveRequest.setReason("allow overall expected result");
        TestCaseVO approved = testCaseDraftService.approveDraft("DRAFT_MISMATCH_001", approveRequest);

        assertThat(approved.getStepsJson()).contains("step_no");
        assertThat(approved.getExpectedResultsJson())
                .isEqualTo("[\"鐢宠鎻愪氦鎴愬姛锛岀郴缁熻繑鍥炵敵璇峰崟 ID\"]");
    }

    @Test
    void listDraftsShouldFilterByReviewStatus() {
        String documentId = "DOC_FILTER_001";
        String generationId = "GEN_FILTER_001";
        createManualDraft("DRAFT_FILTER_PENDING_001", "TC_FILTER_PENDING_001",
                documentId, generationId, ReviewStatusEnum.PENDING.name());
        createManualDraft("DRAFT_FILTER_APPROVED_001", "TC_FILTER_APPROVED_001",
                documentId, generationId, ReviewStatusEnum.APPROVED.name());
        createManualDraft("DRAFT_FILTER_REJECTED_001", "TC_FILTER_REJECTED_001",
                documentId, generationId, ReviewStatusEnum.REJECTED.name());

        List<TestCaseDraftVO> allDrafts = testCaseDraftService.listDrafts(documentId, generationId, null);
        List<TestCaseDraftVO> pendingDrafts = testCaseDraftService.listDrafts(
                documentId, generationId, ReviewStatusEnum.PENDING.name());

        assertThat(allDrafts)
                .extracting(TestCaseDraftVO::getDraftCaseId)
                .containsExactlyInAnyOrder(
                        "DRAFT_FILTER_PENDING_001",
                        "DRAFT_FILTER_APPROVED_001",
                        "DRAFT_FILTER_REJECTED_001");
        assertThat(pendingDrafts)
                .extracting(TestCaseDraftVO::getDraftCaseId)
                .containsExactly("DRAFT_FILTER_PENDING_001");
    }

    @Test
    void batchApproveShouldRemoveApprovedDraftsFromPendingQuery() {
        String documentId = "DOC_BATCH_APPROVE_001";
        String generationId = "GEN_BATCH_APPROVE_001";
        createManualDraft("DRAFT_BATCH_APPROVE_001", "TC_BATCH_APPROVE_001",
                documentId, generationId, ReviewStatusEnum.PENDING.name());
        createManualDraft("DRAFT_BATCH_APPROVE_002", "TC_BATCH_APPROVE_002",
                documentId, generationId, ReviewStatusEnum.PENDING.name());

        TestCaseDraftBatchApproveRequest request = new TestCaseDraftBatchApproveRequest();
        request.setDraftCaseIds(List.of("DRAFT_BATCH_APPROVE_001", "DRAFT_BATCH_APPROVE_002"));
        request.setReviewer("qa");

        List<TestCaseVO> approved = testCaseDraftService.batchApprove(request);
        List<TestCaseDraftVO> pendingDrafts = testCaseDraftService.listDrafts(
                documentId, generationId, ReviewStatusEnum.PENDING.name());
        List<TestCaseVO> formalCases = testCaseService.listCases(documentId, null);

        assertThat(approved).hasSize(2);
        assertThat(pendingDrafts).isEmpty();
        assertThat(formalCases)
                .extracting(TestCaseVO::getSourceDraftCaseId)
                .containsExactlyInAnyOrder("DRAFT_BATCH_APPROVE_001", "DRAFT_BATCH_APPROVE_002");
    }

    private void createManualDraft(String draftCaseId, String caseId,
                                   String documentId, String generationId, String reviewStatus) {
        AiTestopsTestCaseDraft draft = new AiTestopsTestCaseDraft();
        draft.setDraftCaseId(draftCaseId);
        draft.setCaseId(caseId);
        draft.setGenerationId(generationId);
        draft.setDocumentId(documentId);
        draft.setTitle("手工草稿 " + draftCaseId);
        draft.setPreconditionsJson("[]");
        draft.setStepsJson("[{\"step_no\":1,\"action\":\"执行测试操作\"}]");
        draft.setExpectedResultsJson("[\"测试结果符合预期\"]");
        draft.setPriority("P1");
        draft.setCaseType("NORMAL");
        draft.setRiskLevel("P1");
        draft.setRequirementRefsJson("[\"REQ_001\"]");
        draft.setRiskTagsJson("[\"manual\"]");
        draft.setReviewStatus(reviewStatus);
        draft.setRawCaseJson("{}");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        testCaseDraftService.save(draft);
    }

    private TestCaseGenerateVO generateOneDraft() {
        TextDocumentCreateRequest createRequest = new TextDocumentCreateRequest();
        createRequest.setTitle("订单需求");
        createRequest.setContent("用户可以提交订单。\n\n库存不足时提交失败。");
        DocumentVO document = documentService.createTextDocument(createRequest);
        documentService.parseDocument(document.getDocumentId());

        RequirementExtractRequest extractRequest = new RequirementExtractRequest();
        extractRequest.setDocumentId(document.getDocumentId());
        RequirementExtractVO extract = requirementExtractService.extractRequirements(extractRequest);

        TestCaseGenerateRequest generateRequest = new TestCaseGenerateRequest();
        generateRequest.setDocumentId(document.getDocumentId());
        generateRequest.setRequirementExtractId(extract.getRequirementExtractId());
        return testCaseDraftService.generateDrafts(generateRequest);
    }
}
