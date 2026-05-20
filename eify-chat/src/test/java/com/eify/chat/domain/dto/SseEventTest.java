package com.eify.chat.domain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SseEvent")
class SseEventTest {

    @Nested
    @DisplayName("event type constants")
    class EventTypeConstants {

        @Test
        @DisplayName("四种事件类型常量值")
        void shouldHaveCorrectConstantValues() {
            assertThat(SseEvent.EVENT_MESSAGE).isEqualTo("message");
            assertThat(SseEvent.EVENT_COMPLETE).isEqualTo("complete");
            assertThat(SseEvent.EVENT_ERROR).isEqualTo("error");
            assertThat(SseEvent.EVENT_TIMEOUT).isEqualTo("timeout");
        }
    }

    @Nested
    @DisplayName("message factory")
    class MessageFactory {

        @Test
        @DisplayName("创建 message 事件")
        void shouldCreateMessageEvent() {
            SseEvent event = SseEvent.message("hello world");

            assertThat(event.getEvent()).isEqualTo("message");
            assertThat(event.getData()).isInstanceOf(SseEvent.MessageData.class);
            SseEvent.MessageData data = (SseEvent.MessageData) event.getData();
            assertThat(data.getContent()).isEqualTo("hello world");
            assertThat(data.isDone()).isFalse();
        }
    }

    @Nested
    @DisplayName("complete factory")
    class CompleteFactory {

        @Test
        @DisplayName("创建 complete 事件")
        void shouldCreateCompleteEvent() {
            SseEvent.UsageData usage = SseEvent.UsageData.builder()
                    .promptTokens(100)
                    .completionTokens(50)
                    .totalTokens(150)
                    .build();

            SseEvent event = SseEvent.complete(usage, "stop");

            assertThat(event.getEvent()).isEqualTo("complete");
            SseEvent.CompleteData data = (SseEvent.CompleteData) event.getData();
            assertThat(data.isDone()).isTrue();
            assertThat(data.getUsage().getPromptTokens()).isEqualTo(100);
            assertThat(data.getUsage().getCompletionTokens()).isEqualTo(50);
            assertThat(data.getFinishReason()).isEqualTo("stop");
        }
    }

    @Nested
    @DisplayName("error factory")
    class ErrorFactory {

        @Test
        @DisplayName("创建 error 事件")
        void shouldCreateErrorEvent() {
            SseEvent event = SseEvent.error("something went wrong");

            assertThat(event.getEvent()).isEqualTo("error");
            SseEvent.ErrorData data = (SseEvent.ErrorData) event.getData();
            assertThat(data.getError()).isEqualTo("something went wrong");
        }
    }

    @Nested
    @DisplayName("timeout factory")
    class TimeoutFactory {

        @Test
        @DisplayName("创建 timeout 事件")
        void shouldCreateTimeoutEvent() {
            SseEvent event = SseEvent.timeout("request timed out");

            assertThat(event.getEvent()).isEqualTo("timeout");
            SseEvent.TimeoutData data = (SseEvent.TimeoutData) event.getData();
            assertThat(data.isTimeout()).isTrue();
            assertThat(data.getMessage()).isEqualTo("request timed out");
        }
    }

    @Nested
    @DisplayName("internal data classes")
    class InternalDataClasses {

        @Test
        @DisplayName("MessageData.of 工厂方法")
        void shouldCreateMessageData() {
            SseEvent.MessageData data = SseEvent.MessageData.of("test");

            assertThat(data.getContent()).isEqualTo("test");
            assertThat(data.isDone()).isFalse();
        }

        @Test
        @DisplayName("CompleteData.of 工厂方法")
        void shouldCreateCompleteData() {
            SseEvent.UsageData usage = new SseEvent.UsageData(10, 5, 15);

            SseEvent.CompleteData data = SseEvent.CompleteData.of(usage, "length");

            assertThat(data.isDone()).isTrue();
            assertThat(data.getUsage()).isEqualTo(usage);
            assertThat(data.getFinishReason()).isEqualTo("length");
        }

        @Test
        @DisplayName("ErrorData.of 工厂方法")
        void shouldCreateErrorData() {
            SseEvent.ErrorData data = SseEvent.ErrorData.of("error msg");

            assertThat(data.getError()).isEqualTo("error msg");
        }

        @Test
        @DisplayName("TimeoutData.of 工厂方法")
        void shouldCreateTimeoutData() {
            SseEvent.TimeoutData data = SseEvent.TimeoutData.of("timeout msg");

            assertThat(data.isTimeout()).isTrue();
            assertThat(data.getMessage()).isEqualTo("timeout msg");
        }
    }
}
