package com.example.aitestops.testcase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.testcase.dto.TestCaseDraftBatchApproveRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftReviewRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftUpdateRequest;
import com.example.aitestops.testcase.dto.TestCaseGenerateRequest;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.example.aitestops.testcase.vo.TestCaseDraftVO;
import com.example.aitestops.testcase.vo.TestCaseGenerateVO;
import com.example.aitestops.testcase.vo.TestCaseVO;

import java.util.List;

/**
 * 测试用例草稿服务。
 */
public interface AiTestopsTestCaseDraftService extends IService<AiTestopsTestCaseDraft> {

    TestCaseGenerateVO generateDrafts(TestCaseGenerateRequest request);

    List<TestCaseDraftVO> listDrafts(String documentId, String generationId, String reviewStatus);

    TestCaseDraftVO getDraft(String draftCaseId);

    TestCaseDraftVO updateDraft(String draftCaseId, TestCaseDraftUpdateRequest request);

    TestCaseVO approveDraft(String draftCaseId, TestCaseDraftReviewRequest request);

    TestCaseDraftVO rejectDraft(String draftCaseId, TestCaseDraftReviewRequest request);

    List<TestCaseVO> batchApprove(TestCaseDraftBatchApproveRequest request);
}
