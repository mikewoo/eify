package com.eify.workflow.domain.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowExecuteRequest {

    /** 运行时输入变量，如 {"user_input": "我要退货", "order_id": "12345"} */
    private Map<String, Object> variables;
}
