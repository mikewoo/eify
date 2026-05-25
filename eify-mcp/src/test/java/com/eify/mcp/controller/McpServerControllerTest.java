package com.eify.mcp.controller;

import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.service.McpServerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpServerController")
class McpServerControllerTest {

    @Mock McpServerService mcpServerService;

    @InjectMocks McpServerController controller;

    @Nested
    @DisplayName("GET /api/v1/mcp-servers/tools")
    class ListToolsTests {

        @Test
        @DisplayName("enabled=1 参数透传给 Service")
        void shouldPassEnabledParam() {
            McpServerResponse resp = McpServerResponse.builder()
                    .id(1L).name("S1").online(true).tools(List.of()).build();
            when(mcpServerService.listToolsByWorkspace(eq(1))).thenReturn(List.of(resp));

            var result = controller.listTools(1);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getName()).isEqualTo("S1");
            verify(mcpServerService).listToolsByWorkspace(1);
        }

        @Test
        @DisplayName("不传 enabled 时传 null")
        void shouldPassNullWhenNoParam() {
            when(mcpServerService.listToolsByWorkspace(isNull())).thenReturn(List.of());

            var result = controller.listTools(null);

            assertThat(result.getData()).isEmpty();
            verify(mcpServerService).listToolsByWorkspace(null);
        }
    }
}
