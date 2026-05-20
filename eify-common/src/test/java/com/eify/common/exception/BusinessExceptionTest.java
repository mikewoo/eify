package com.eify.common.exception;

import com.eify.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessException")
class BusinessExceptionTest {

    @Nested
    @DisplayName("constructor with ErrorCode")
    class ConstructorWithErrorCode {

        @Test
        @DisplayName("使用 ErrorCode 构造保留 code 和 message")
        void shouldPreserveCodeAndMessage() {
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR);

            assertThat(ex.getCode()).isEqualTo(1001);
            assertThat(ex.getMessage()).isEqualTo("参数错误");
        }

        @Test
        @DisplayName("使用 ErrorCode.SUCCESS 构造")
        void shouldWorkWithSuccessCode() {
            BusinessException ex = new BusinessException(ErrorCode.SUCCESS);

            assertThat(ex.getCode()).isEqualTo(200);
            assertThat(ex.getMessage()).isEqualTo("操作成功");
        }
    }

    @Nested
    @DisplayName("constructor with ErrorCode and custom message")
    class ConstructorWithErrorCodeAndMessage {

        @Test
        @DisplayName("自定义消息覆盖 ErrorCode 默认 message")
        void shouldUseCustomMessage() {
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, "用户名不能为空");

            assertThat(ex.getCode()).isEqualTo(1001);
            assertThat(ex.getMessage()).isEqualTo("用户名不能为空");
        }
    }

    @Nested
    @DisplayName("fillInStackTrace")
    class FillInStackTrace {

        @Test
        @DisplayName("不填充堆栈跟踪以提升性能")
        void shouldNotFillStackTrace() {
            BusinessException ex = new BusinessException(ErrorCode.SYSTEM_ERROR);

            assertThat(ex.getStackTrace()).isEmpty();
        }
    }

    @Nested
    @DisplayName("inheritance")
    class Inheritance {

        @Test
        @DisplayName("BusinessException 是 RuntimeException 的子类")
        void shouldBeRuntimeException() {
            BusinessException ex = new BusinessException(ErrorCode.SYSTEM_ERROR);

            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
