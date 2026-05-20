package com.eify.common.result;

import com.eify.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Result")
class ResultTest {

    @Nested
    @DisplayName("success")
    class Success {

        @Test
        @DisplayName("success() 返回 code=200, message=操作成功, data=true")
        void shouldReturnSuccessWithoutData() {
            Result<Boolean> result = Result.success();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("操作成功");
            assertThat(result.getData()).isEqualTo(true);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("success(data) 返回 code=200 并附带数据")
        void shouldReturnSuccessWithData() {
            Result<String> result = Result.success("hello");

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("操作成功");
            assertThat(result.getData()).isEqualTo("hello");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("success(data) 支持 null 数据")
        void shouldReturnSuccessWithNullData() {
            Result<Object> result = Result.success(null);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("success(message, data) 返回自定义消息和数据")
        void shouldReturnSuccessWithCustomMessageAndData() {
            Result<Integer> result = Result.success("创建成功", 123);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("创建成功");
            assertThat(result.getData()).isEqualTo(123);
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("fail")
    class Fail {

        @Test
        @DisplayName("fail() 返回默认 SYSTEM_ERROR")
        void shouldReturnDefaultFail() {
            Result<Object> result = Result.fail();

            assertThat(result.getCode()).isEqualTo(1000);
            assertThat(result.getMessage()).isEqualTo("系统内部错误");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(message) 返回自定义消息，code 仍为 SYSTEM_ERROR")
        void shouldReturnFailWithCustomMessage() {
            Result<Object> result = Result.fail("数据库连接失败");

            assertThat(result.getCode()).isEqualTo(1000);
            assertThat(result.getMessage()).isEqualTo("数据库连接失败");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(code, message) 返回自定义 code 和 message")
        void shouldReturnFailWithCustomCodeAndMessage() {
            Result<Object> result = Result.fail(4001, "消息不存在");

            assertThat(result.getCode()).isEqualTo(4001);
            assertThat(result.getMessage()).isEqualTo("消息不存在");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(ErrorCode) 使用 ErrorCode 枚举")
        void shouldReturnFailWithErrorCode() {
            Result<Object> result = Result.fail(ErrorCode.WORKFLOW_NOT_FOUND);

            assertThat(result.getCode()).isEqualTo(6000);
            assertThat(result.getMessage()).isEqualTo("工作流不存在");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(ErrorCode, message) 使用 ErrorCode 枚举 + 自定义消息")
        void shouldReturnFailWithErrorCodeAndCustomMessage() {
            Result<Object> result = Result.fail(ErrorCode.PARAM_ERROR, "邮箱格式不正确");

            assertThat(result.getCode()).isEqualTo(1001);
            assertThat(result.getMessage()).isEqualTo("邮箱格式不正确");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("isSuccess")
    class IsSuccess {

        @Test
        @DisplayName("code 为 200 时返回 true")
        void shouldReturnTrueWhenCodeIs200() {
            Result<Object> result = new Result<>(200, "OK", null);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("code 为 null 时返回 false")
        void shouldReturnFalseWhenCodeIsNull() {
            Result<Object> result = new Result<>(null, "msg", null);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("code 不是 200 时返回 false")
        void shouldReturnFalseWhenCodeNot200() {
            Result<Object> result = new Result<>(201, "Created", null);
            assertThat(result.isSuccess()).isFalse();

            Result<Object> result2 = new Result<>(0, "zero", null);
            assertThat(result2.isSuccess()).isFalse();
        }
    }
}
