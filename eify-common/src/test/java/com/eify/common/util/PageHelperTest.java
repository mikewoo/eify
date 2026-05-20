package com.eify.common.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageHelper")
class PageHelperTest {

    @Nested
    @DisplayName("toPage")
    class ToPage {

        @Test
        @DisplayName("正常参数转换为 Page 对象")
        void shouldCreatePageWithNormalParams() {
            Page<String> page = PageHelper.toPage(2, 30);

            assertThat(page.getCurrent()).isEqualTo(2L);
            assertThat(page.getSize()).isEqualTo(30L);
        }

        @Test
        @DisplayName("page 为 null 时默认第 1 页")
        void shouldDefaultToFirstPageWhenNull() {
            Page<String> page = PageHelper.toPage(null, 20);

            assertThat(page.getCurrent()).isEqualTo(1L);
        }

        @Test
        @DisplayName("page <= 0 时默认第 1 页")
        void shouldDefaultToFirstPageWhenNonPositive() {
            Page<String> page = PageHelper.toPage(0, 20);

            assertThat(page.getCurrent()).isEqualTo(1L);
        }

        @Test
        @DisplayName("pageSize 为 null 时默认 20")
        void shouldDefaultPageSizeWhenNull() {
            Page<String> page = PageHelper.toPage(1, null);

            assertThat(page.getSize()).isEqualTo(20L);
        }

        @Test
        @DisplayName("pageSize <= 0 时默认 20")
        void shouldDefaultPageSizeWhenNonPositive() {
            Page<String> page = PageHelper.toPage(1, 0);

            assertThat(page.getSize()).isEqualTo(20L);
        }

        @Test
        @DisplayName("pageSize > 100 时限制为 100")
        void shouldCapPageSizeAt100() {
            Page<String> page = PageHelper.toPage(1, 200);

            assertThat(page.getSize()).isEqualTo(100L);
        }

        @Test
        @DisplayName("pageSize 正好 100 时通过")
        void shouldAllowPageSize100() {
            Page<String> page = PageHelper.toPage(1, 100);

            assertThat(page.getSize()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("toPage (no-arg)")
    class ToPageNoArg {

        @Test
        @DisplayName("无参重载返回 page=1, pageSize=20")
        void shouldCreateDefaultPage() {
            Page<String> page = PageHelper.toPage();

            assertThat(page.getCurrent()).isEqualTo(1L);
            assertThat(page.getSize()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("toPageResult")
    class ToPageResult {

        @Test
        @DisplayName("IPage 转换为 PageResult")
        void shouldConvertIPageToPageResult() {
            Page<String> iPage = new Page<>(1, 20);
            iPage.setRecords(List.of("a", "b"));
            iPage.setTotal(50L);

            PageResult<String> result = PageHelper.toPageResult(iPage);

            assertThat(result.getList()).containsExactly("a", "b");
            assertThat(result.getTotal()).isEqualTo(50L);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("空结果转换")
        void shouldConvertEmptyIPage() {
            Page<String> iPage = new Page<>(1, 20);
            iPage.setRecords(List.of());
            iPage.setTotal(0L);

            PageResult<String> result = PageHelper.toPageResult(iPage);

            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.hasData()).isFalse();
        }
    }

    @Nested
    @DisplayName("getOffset")
    class GetOffset {

        @Test
        @DisplayName("第 1 页偏移为 0")
        void shouldReturnZeroForFirstPage() {
            assertThat(PageHelper.getOffset(1, 20)).isEqualTo(0L);
        }

        @Test
        @DisplayName("第 3 页偏移正确")
        void shouldCalculateOffsetForLaterPage() {
            assertThat(PageHelper.getOffset(3, 20)).isEqualTo(40L);
        }

        @Test
        @DisplayName("自定义 pageSize 偏移")
        void shouldCalculateOffsetWithCustomSize() {
            assertThat(PageHelper.getOffset(2, 15)).isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("getTotalPages")
    class GetTotalPages {

        @Test
        @DisplayName("刚好整除")
        void shouldCalculateExactPages() {
            assertThat(PageHelper.getTotalPages(100, 20)).isEqualTo(5L);
        }

        @Test
        @DisplayName("有余数向上取整")
        void shouldRoundUp() {
            assertThat(PageHelper.getTotalPages(101, 20)).isEqualTo(6L);
        }

        @Test
        @DisplayName("total 为 0 时返回 0")
        void shouldReturnZeroForZeroTotal() {
            assertThat(PageHelper.getTotalPages(0, 20)).isEqualTo(0L);
        }

        @Test
        @DisplayName("pageSize 为 0 时返回 0")
        void shouldReturnZeroForZeroPageSize() {
            assertThat(PageHelper.getTotalPages(100, 0)).isEqualTo(0L);
        }
    }
}
