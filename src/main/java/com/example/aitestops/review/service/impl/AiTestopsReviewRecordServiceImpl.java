package com.example.aitestops.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.common.enums.ReviewActionEnum;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.review.entity.AiTestopsReviewRecord;
import com.example.aitestops.review.mapper.AiTestopsReviewRecordMapper;
import com.example.aitestops.review.service.AiTestopsReviewRecordService;
import com.example.aitestops.review.vo.ReviewRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工评审记录服务实现。
 */
@Slf4j
@Service
public class AiTestopsReviewRecordServiceImpl
        extends ServiceImpl<AiTestopsReviewRecordMapper, AiTestopsReviewRecord>
        implements AiTestopsReviewRecordService {

    @Override
    public void record(String caseId, String draftCaseId, String testCaseId, ReviewActionEnum action,
                       String beforeJson, String afterJson, String reason, String reviewer) {
        AiTestopsReviewRecord record = new AiTestopsReviewRecord();
        record.setReviewRecordId(IdGenerator.reviewRecordId());
        record.setCaseId(caseId);
        record.setDraftCaseId(draftCaseId);
        record.setTestCaseId(testCaseId);
        record.setAction(action.name());
        record.setBeforeJson(beforeJson);
        record.setAfterJson(afterJson);
        record.setReason(reason);
        record.setReviewer(StringUtils.hasText(reviewer) ? reviewer : "manual_user");
        record.setCreatedAt(LocalDateTime.now());
        save(record);
        log.info("人工评审记录保存: caseId={}, draftCaseId={}, testCaseId={}, action={}",
                caseId, draftCaseId, testCaseId, action);
    }

    @Override
    public List<ReviewRecordVO> listByCaseId(String caseId) {
        log.info("查询人工评审记录: caseId={}", caseId);
        return list(new LambdaQueryWrapper<AiTestopsReviewRecord>()
                .eq(AiTestopsReviewRecord::getCaseId, caseId)
                .or()
                .eq(AiTestopsReviewRecord::getDraftCaseId, caseId)
                .or()
                .eq(AiTestopsReviewRecord::getTestCaseId, caseId)
                .orderByDesc(AiTestopsReviewRecord::getCreatedAt))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private ReviewRecordVO toVO(AiTestopsReviewRecord record) {
        ReviewRecordVO vo = new ReviewRecordVO();
        vo.setReviewRecordId(record.getReviewRecordId());
        vo.setCaseId(record.getCaseId());
        vo.setDraftCaseId(record.getDraftCaseId());
        vo.setTestCaseId(record.getTestCaseId());
        vo.setAction(record.getAction());
        vo.setBeforeJson(record.getBeforeJson());
        vo.setAfterJson(record.getAfterJson());
        vo.setReason(record.getReason());
        vo.setReviewer(record.getReviewer());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
