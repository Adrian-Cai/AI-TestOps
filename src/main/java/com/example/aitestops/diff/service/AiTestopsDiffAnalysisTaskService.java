package com.example.aitestops.diff.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.diff.dto.DiffAnalysisTaskCreateRequest;
import com.example.aitestops.diff.dto.DiffRiskActionRequest;
import com.example.aitestops.diff.dto.DiffRiskVerifyRequest;
import com.example.aitestops.diff.entity.AiTestopsDiffAnalysisTask;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisSourceVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.diff.vo.DiffSupplementCaseVO;

import java.util.List;

/**
 * Diff 分析任务服务接口。
 * <p>
 * 提供代码 Diff 分析任务的全生命周期管理，包括创建分析任务、
 * 查询任务列表、获取分析报告、处理风险项和生成补充用例等功能。
 * </p>
 */
public interface AiTestopsDiffAnalysisTaskService extends IService<AiTestopsDiffAnalysisTask> {

    /**
     * 创建并执行 Diff 分析任务。
     *
     * @param request 任务创建请求
     * @return 任务视图对象
     */
    DiffAnalysisTaskVO createAndAnalyze(DiffAnalysisTaskCreateRequest request);

    /**
     * 查询 Diff 分析任务列表。
     *
     * @param documentId          需求文档 ID（可选）
     * @param requirementExtractId 需求提取 ID（可选）
     * @param status              任务状态（可选）
     * @param sourceBranch        源分支（可选）
     * @param targetBranch        目标分支（可选）
     * @return 任务列表
     */
    List<DiffAnalysisTaskVO> listTasks(String documentId, String requirementExtractId, String status,
                                       String sourceBranch, String targetBranch);

    /**
     * 查询最近可用于 Diff 分析的需求来源。
     *
     * @param limit 返回数量限制（可选）
     * @return 需求来源列表
     */
    List<DiffAnalysisSourceVO> listRecentSources(Integer limit);

    /**
     * 查询 Git 仓库分支列表。
     *
     * @param repoUrl 仓库地址
     * @return 分支名称列表
     */
    List<String> listRepositoryBranches(String repoUrl);

    /**
     * 获取 Diff 分析报告。
     *
     * @param taskId 任务 ID
     * @return 分析报告视图对象
     */
    DiffAnalysisReportVO getReport(Long taskId);

    /**
     * 执行风险处理动作。
     *
     * @param riskId  风险项 ID
     * @param request 动作请求
     */
    void applyRiskAction(Long riskId, DiffRiskActionRequest request);

    /**
     * 标记风险验证结果。
     *
     * @param riskId  风险项 ID
     * @param request 验证请求
     */
    void verifyRisk(Long riskId, DiffRiskVerifyRequest request);

    /**
     * 生成 Diff 风险补充测试用例草稿。
     *
     * @param riskId 风险项 ID
     * @return 补充用例视图对象
     */
    DiffSupplementCaseVO generateSupplementCases(Long riskId);
}
