package com.eify.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CursorPageRequest")
class CursorPageRequestTest {

    @Nested
    @DisplayName("default constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("默认构造函数 pageSize=20, lastId=null")
        void shouldHaveDefaultValues() {
            CursorPageRequest request = new CursorPageRequest();

            assertThat(request.getLastId()).isNull();
            assertThat(request.getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("full constructor")
    class FullConstructor {

        @Test
        @DisplayName("全参构造函数正确赋值")
        void shouldAssignAllFields() {
            CursorPageRequest request = new CursorPageRequest(100L, 50);

            assertThat(request.getLastId()).isEqualTo(100L);
            assertThat(request.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("pageSize 为 null 时取默认值 20")
        void shouldDefaultPageSizeWhenNull() {
            CursorPageRequest request = new CursorPageRequest(100L, null);

            assertThat(request.getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("setters")
    class Setters {

        @Test
        @DisplayName("setLastId 正确赋值")
        void shouldSetLastId() {
            CursorPageRequest request = new CursorPageRequest();
            request.setLastId(42L);

            assertThat(request.getLastId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("setLastId(null) 赋值为 null")
        void shouldSetLastIdToNull() {
            CursorPageRequest request = new CursorPageRequest(100L, 20);
            request.setLastId(null);

            assertThat(request.getLastId()).isNull();
        }

        @Test
        @DisplayName("setPageSize 正确赋值")
        void shouldSetPageSize() {
            CursorPageRequest request = new CursorPageRequest();
            request.setPageSize(50);

            assertThat(request.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("setPageSize(null) 取默认值 20")
        void shouldDefaultPageSizeWhenSetNull() {
            CursorPageRequest request = new CursorPageRequest(100L, 50);
            request.setPageSize(null);

            assertThat(request.getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("builder 设置 lastId 和 pageSize")
        void shouldBuildWithLastIdAndPageSize() {
            CursorPageRequest request = CursorPageRequest.builder()
                    .lastId(200L)
                    .pageSize(30)
                    .build();

            assertThat(request.getLastId()).isEqualTo(200L);
            assertThat(request.getPageSize()).isEqualTo(30);
        }

        @Test
        @DisplayName("builder 仅设置 lastId，pageSize 取默认值")
        void shouldBuildWithDefaultPageSize() {
            CursorPageRequest request = CursorPageRequest.builder()
                    .lastId(200L)
                    .build();

            assertThat(request.getLastId()).isEqualTo(200L);
            assertThat(request.getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("builder 仅设置 pageSize")
        void shouldBuildWithOnlyPageSize() {
            CursorPageRequest request = CursorPageRequest.builder()
                    .pageSize(50)
                    .build();

            assertThat(request.getLastId()).isNull();
            assertThat(request.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("builder 空构建取默认值")
        void shouldBuildWithAllDefaults() {
            CursorPageRequest request = CursorPageRequest.builder().build();

            assertThat(request.getLastId()).isNull();
            assertThat(request.getPageSize()).isEqualTo(20);
        }
    }
}
