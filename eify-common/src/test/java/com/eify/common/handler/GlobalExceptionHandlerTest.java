package com.eify.common.handler;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.Result;
import com.eify.common.util.MessageUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler")
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    MessageUtil messageUtil;

    @InjectMocks
    GlobalExceptionHandler handler;

    @Nested
    @DisplayName("handleBusinessException")
    class BusinessExceptionTests {

        @Test
        @DisplayName("Should localize ErrorCode message via MessageUtil")
        void shouldLocalizeErrorCodeMessage() {
            when(messageUtil.get(ErrorCode.KNOWLEDGE_NOT_FOUND))
                    .thenReturn("Knowledge base not found");
            BusinessException ex = new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);

            Result<Void> result = handler.handleBusinessException(ex);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getCode()).isEqualTo(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode());
            assertThat(result.getMessage()).isEqualTo("Knowledge base not found");
        }

        @Test
        @DisplayName("Should preserve custom message without localization")
        void shouldPreserveCustomMessage() {
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, "custom detail");

            Result<Void> result = handler.handleBusinessException(ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo("custom detail");
        }
    }

    @Nested
    @DisplayName("handleMethodArgumentNotValidException")
    class MethodArgumentNotValidTests {

        @Test
        @DisplayName("Should join all field error messages")
        void shouldJoinFieldErrors() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("obj", "name", "Name must not be empty"),
                    new FieldError("obj", "email", "Invalid email format")
            ));

            Result<Void> result = handler.handleMethodArgumentNotValidException(ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).contains("Name must not be empty", "Invalid email format");
        }

        @Test
        @DisplayName("Should handle single field error")
        void shouldHandleSingleFieldError() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("obj", "name", "Name must not be empty")
            ));

            Result<Void> result = handler.handleMethodArgumentNotValidException(ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        }
    }

    @Nested
    @DisplayName("handleBindException")
    class BindExceptionTests {

        @Test
        @DisplayName("Should join field error messages and return PARAM_ERROR")
        void shouldJoinFieldErrors() {
            BindException ex = mock(BindException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("obj", "field", "field error")
            ));

            Result<Void> result = handler.handleBindException(ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo("field error");
        }
    }

    @Nested
    @DisplayName("handleIllegalArgumentException")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("Should return PARAM_ERROR with exception message")
        void shouldReturnParamErrorWithMessage() {
            Result<Void> result = handler.handleIllegalArgumentException(
                    new IllegalArgumentException("invalid value"));

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo("invalid value");
        }
    }

    @Nested
    @DisplayName("handleHttpMessageNotReadableException")
    class HttpMessageNotReadableTests {

        @Test
        @DisplayName("Should return localized request body format error")
        void shouldReturnLocalizedMessage() {
            when(messageUtil.get("REQ_BODY_FORMAT_ERROR"))
                    .thenReturn("Invalid request body format");

            Result<Void> result = handler.handleHttpMessageNotReadableException(
                    new HttpMessageNotReadableException("parse error", (org.springframework.http.HttpInputMessage) null));

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo("Invalid request body format");
        }
    }

    @Nested
    @DisplayName("handleMissingServletRequestParameterException")
    class MissingParamTests {

        @Test
        @DisplayName("Should include parameter name in localized message")
        void shouldIncludeParamName() {
            when(messageUtil.get(eq("MISSING_PARAM"), any(Object[].class)))
                    .thenReturn("Missing parameter: userId");

            Result<Void> result = handler.handleMissingServletRequestParameterException(
                    new MissingServletRequestParameterException("userId", "Long"));

            assertThat(result.getMessage()).contains("userId");
        }
    }

    @Nested
    @DisplayName("handleHttpRequestMethodNotSupportedException")
    class MethodNotSupportedTests {

        @Test
        @DisplayName("Should include method in localized message")
        void shouldIncludeMethod() {
            when(messageUtil.get(eq("UNSUPPORTED_METHOD"), any(Object[].class)))
                    .thenReturn("Unsupported method: POST");

            Result<Void> result = handler.handleHttpRequestMethodNotSupportedException(
                    new HttpRequestMethodNotSupportedException("POST"));

            assertThat(result.getMessage()).contains("POST");
        }
    }

    @Nested
    @DisplayName("handleNoHandlerFoundException")
    class NoHandlerFoundTests {

        @Test
        @DisplayName("Should return NOT_FOUND error code")
        void shouldReturnNotFound() {
            when(messageUtil.get("ENDPOINT_NOT_FOUND")).thenReturn("Endpoint not found");
            NoHandlerFoundException ex = mock(NoHandlerFoundException.class);
            when(ex.getHttpMethod()).thenReturn("GET");
            when(ex.getRequestURL()).thenReturn("/api/unknown");

            Result<Void> result = handler.handleNoHandlerFoundException(ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("handleHttpMediaTypeNotSupportedException")
    class MediaTypeNotSupportedTests {

        @Test
        @DisplayName("Should return localized unsupported Content-Type")
        void shouldReturnUnsupportedContentType() {
            when(messageUtil.get("UNSUPPORTED_CONTENT_TYPE"))
                    .thenReturn("Unsupported Content-Type");
            HttpMediaTypeNotSupportedException ex = mock(HttpMediaTypeNotSupportedException.class);

            Result<Void> result = handler.handleHttpMediaTypeNotSupportedException(ex);

            assertThat(result.getMessage()).isEqualTo("Unsupported Content-Type");
        }
    }

    @Nested
    @DisplayName("handleException (catch-all)")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return SYSTEM_ERROR without leaking original exception details")
        void shouldReturnSystemErrorWithoutStacktrace() {
            when(messageUtil.get(ErrorCode.SYSTEM_ERROR))
                    .thenReturn("Internal server error");

            Result<Void> result = handler.handleException(
                    new NullPointerException("sensitive NPE detail"));

            assertThat(result.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo("Internal server error");
            assertThat(result.getMessage()).doesNotContain("NPE");
        }
    }
}
