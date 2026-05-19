package com.example.aitestops.common.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successShouldUseZeroCodeAndCarryData() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTraceId()).isNull();
    }

    @Test
    void successWithoutDataShouldReturnVoidResponse() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();
    }

    @Test
    void failShouldCarryCodeMessageAndOptionalTraceId() {
        ApiResponse<Void> response = ApiResponse.fail(40001, "参数错误", "TRACE_1");

        assertThat(response.getCode()).isEqualTo(40001);
        assertThat(response.getMessage()).isEqualTo("参数错误");
        assertThat(response.getData()).isNull();
        assertThat(response.getTraceId()).isEqualTo("TRACE_1");
    }
}
