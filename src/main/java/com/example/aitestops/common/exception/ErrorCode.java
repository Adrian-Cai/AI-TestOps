package com.example.aitestops.common.exception;

import lombok.Getter;

/**
 * 业务错误码定义，第一阶段聚焦文件和文档解析错误。
 */
@Getter
public enum ErrorCode {

    BAD_REQUEST(400, "请求参数不合法"),
    RESOURCE_NOT_FOUND(404, "资源不存在"),
    FILE_EMPTY(400, "文件为空"),
    FILE_TYPE_UNSUPPORTED(400, "文件类型不支持"),
    FILE_TOO_LARGE(400, "文件超过大小限制"),
    FILE_SAVE_FAILED(500, "文件保存失败"),
    TIKA_PARSE_FAILED(500, "Tika 解析失败"),
    DOCUMENT_NOT_FOUND(404, "文档不存在"),
    DOCUMENT_NOT_PARSED(400, "文档未解析成功"),
    PROMPT_TEMPLATE_NOT_FOUND(404, "Prompt 模板不存在"),
    AI_CONFIG_INVALID(500, "AI 模型配置不合法"),
    AI_CALL_FAILED(500, "AI 调用失败"),
    AI_OUTPUT_INVALID(500, "AI 输出不是合法 JSON"),
    GENERATION_NOT_FOUND(404, "生成记录不存在"),
    REQUIREMENT_EXTRACT_NOT_FOUND(404, "需求解析结果不存在"),
    TEST_CASE_DRAFT_NOT_FOUND(404, "测试用例草稿不存在"),
    TEST_CASE_NOT_FOUND(404, "正式测试用例不存在"),
    TEST_CASE_DRAFT_ALREADY_APPROVED(400, "已确认的草稿不能重复确认"),
    VALIDATION_FAILED(500, "AI 输出校验失败"),
    DATABASE_SAVE_FAILED(500, "数据库保存失败"),
    INTERNAL_ERROR(500, "系统异常");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
