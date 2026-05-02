package com.example.aitestops.review.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.common.enums.ReviewActionEnum;
import com.example.aitestops.review.entity.AiTestopsReviewRecord;
import com.example.aitestops.review.vo.ReviewRecordVO;

import java.util.List;

/**
 * 人工评审记录服务。
 */
public interface AiTestopsReviewRecordService extends IService<AiTestopsReviewRecord> {

    void record(String caseId, String draftCaseId, String testCaseId, ReviewActionEnum action,
                String beforeJson, String afterJson, String reason, String reviewer);

    List<ReviewRecordVO> listByCaseId(String caseId);
}
