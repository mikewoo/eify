package com.eify.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 配置（Jackson 3 / tools.jackson）
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            builder.enable(SerializationFeature.INDENT_OUTPUT);
        };
    }

    @Bean
    public ObjectMapper objectMapper(JsonMapper.Builder builder) {
        return builder.build();
    }
}
