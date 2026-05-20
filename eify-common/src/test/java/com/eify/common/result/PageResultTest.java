package com.eify.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResult")
class PageResultTest {

    @Nested
    @DisplayName("traditional pagination")
    class TraditionalPagination {

        @Test
        @DisplayName("of() 构造传统分页结果")
        void shouldCreateTraditionalPageResult() {
            List<String> data = List.of("a", "b", "c");
            PageResult<String> result = PageResult.of(data, 100L, 1, 20);

            assertThat(result.getList()).containsExactly("a", "b", "c");
            assertThat(result.getTotal()).isEqualTo(100L);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(20);
            assertThat(result.getHasMore()).isNull();
            assertThat(result.isCursorMode()).isFalse();
        }

        @Test
        @DisplayName("empty() 构造空传统分页")
        void shouldCreateEmptyTraditionalPage() {
            PageResult<String> result = PageResult.empty(1, 20);

            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(20);
            assertThat(result.hasData()).isFalse();
        }
    }

    @Nested
    @DisplayName("cursor pagination")
    class CursorPagination {

        @Test
        @DisplayName("ofCursor() 构造游标分页结果")
        void shouldCreateCursorPageResult() {
            List<String> data = List.of("x", "y");
            PageResult<String> result = PageResult.ofCursor(data, 10, true);

            assertThat(result.getList()).containsExactly("x", "y");
            assertThat(result.getTotal()).isNull();
            assertThat(result.getPage()).isNull();
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getHasMore()).isTrue();
            assertThat(result.isCursorMode()).isTrue();
        }

        @Test
        @DisplayName("ofCursor() hasMore=false 表示最后一页")
        void shouldCreateCursorPageResultLastPage() {
            PageResult<String> result = PageResult.ofCursor(List.of("z"), 10, false);

            assertThat(result.getHasMore()).isFalse();
            assertThat(result.isCursorMode()).isTrue();
        }
    }

    @Nested
    @DisplayName("isCursorMode")
    class IsCursorMode {

        @Test
        @DisplayName("total 为 null 时返回 true")
        void shouldReturnTrueWhenTotalIsNull() {
            PageResult<String> result = PageResult.ofCursor(List.of(), 10, false);
            assertThat(result.isCursorMode()).isTrue();
        }

        @Test
        @DisplayName("total 不为 null 时返回 false")
        void shouldReturnFalseWhenTotalIsNotNull() {
            PageResult<String> result = PageResult.of(List.of(), 0L, 1, 20);
            assertThat(result.isCursorMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasData")
    class HasData {

        @Test
        @DisplayName("list 为空时返回 false")
        void shouldReturnFalseForEmptyList() {
            PageResult<String> result = PageResult.empty(1, 20);
            assertThat(result.hasData()).isFalse();
        }

        @Test
        @DisplayName("list 为 null 时返回 false")
        void shouldReturnFalseForNullList() {
            PageResult<String> result = new PageResult<>();
            assertThat(result.hasData()).isFalse();
        }

        @Test
        @DisplayName("list 有数据时返回 true")
        void shouldReturnTrueForNonEmptyList() {
            PageResult<String> result = PageResult.of(List.of("a"), 1L, 1, 20);
            assertThat(result.hasData()).isTrue();
        }
    }

    @Nested
    @DisplayName("hasNext")
    class HasNext {

        @Test
        @DisplayName("游标模式：hasMore=true 时有下一页")
        void shouldReturnTrueWhenCursorHasMore() {
            PageResult<String> result = PageResult.ofCursor(List.of("a"), 10, true);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("游标模式：hasMore=false 时无下一页")
        void shouldReturnFalseWhenCursorNoMore() {
            PageResult<String> result = PageResult.ofCursor(List.of("a"), 10, false);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("传统分页：page * pageSize < total 时有下一页")
        void shouldReturnTrueWhenTraditionalHasNext() {
            PageResult<String> result = PageResult.of(List.of("a"), 100L, 1, 20);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("传统分页：page * pageSize >= total 时无下一页")
        void shouldReturnFalseWhenTraditionalNoNext() {
            PageResult<String> result = PageResult.of(List.of("a"), 20L, 1, 20);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("传统分页：最后一页无下一页")
        void shouldReturnFalseOnLastPage() {
            PageResult<String> result = PageResult.of(List.of("a"), 100L, 5, 20);
            assertThat(result.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasPrevious")
    class HasPrevious {

        @Test
        @DisplayName("第一页无上一页")
        void shouldReturnFalseOnFirstPage() {
            PageResult<String> result = PageResult.of(List.of("a"), 100L, 1, 20);
            assertThat(result.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("非第一页有上一页")
        void shouldReturnTrueOnNonFirstPage() {
            PageResult<String> result = PageResult.of(List.of("a"), 100L, 3, 20);
            assertThat(result.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("游标模式不适用 hasPrevious")
        void shouldReturnFalseForCursorMode() {
            PageResult<String> result = PageResult.ofCursor(List.of("a"), 10, true);
            assertThat(result.hasPrevious()).isFalse();
        }
    }

    @Nested
    @DisplayName("getTotalPages")
    class GetTotalPages {

        @Test
        @DisplayName("刚好整除页数")
        void shouldReturnExactPages() {
            PageResult<String> result = PageResult.of(List.of("a"), 100L, 1, 20);
            assertThat(result.getTotalPages()).isEqualTo(5);
        }

        @Test
        @DisplayName("有余数时向上取整")
        void shouldRoundUp() {
            PageResult<String> result = PageResult.of(List.of("a"), 101L, 1, 20);
            assertThat(result.getTotalPages()).isEqualTo(6);
        }

        @Test
        @DisplayName("游标模式返回 null")
        void shouldReturnNullForCursorMode() {
            PageResult<String> result = PageResult.ofCursor(List.of("a"), 10, true);
            assertThat(result.getTotalPages()).isNull();
        }
    }

    @Nested
    @DisplayName("default constructor and setters")
    class DefaultConstructor {

        @Test
        @DisplayName("默认构造函数创建空对象")
        void shouldCreateEmptyPageResult() {
            PageResult<String> result = new PageResult<>();
            assertThat(result.getList()).isNull();
            assertThat(result.getTotal()).isNull();
            assertThat(result.isCursorMode()).isTrue();
        }

        @Test
        @DisplayName("setter 可设置所有字段")
        void shouldSetAllFields() {
            PageResult<String> result = new PageResult<>();
            result.setList(List.of("x"));
            result.setTotal(10L);
            result.setPage(1);
            result.setPageSize(10);
            result.setHasMore(false);

            assertThat(result.getList()).containsExactly("x");
            assertThat(result.getTotal()).isEqualTo(10L);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getHasMore()).isFalse();
        }
    }

    @Nested
    @DisplayName("full constructor")
    class FullConstructor {

        @Test
        @DisplayName("全参构造函数设置所有字段")
        void shouldSetAllFieldsViaFullConstructor() {
            List<String> data = List.of("a");
            PageResult<String> result = new PageResult<>(data, 50L, 2, 25, true);

            assertThat(result.getList()).isEqualTo(data);
            assertThat(result.getTotal()).isEqualTo(50L);
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getPageSize()).isEqualTo(25);
            assertThat(result.getHasMore()).isTrue();
        }
    }
}
