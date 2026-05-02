package com.example.aitestops.testcase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.vo.TestCaseVO;

import java.util.List;

/**
 * 正式测试用例服务。
 */
public interface AiTestopsTestCaseService extends IService<AiTestopsTestCase> {

    List<TestCaseVO> listCases(String documentId, String requirementExtractId);
}
