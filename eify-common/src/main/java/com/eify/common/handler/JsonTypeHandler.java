package com.eify.common.handler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 类型处理器
 * <p>
 * 用于 MyBatis-Plus 处理 JSON 字段与 Java 对象的转换
 * <p>
 * 注意：不使用 @MappedTypes({Object.class})，避免拦截 LocalDateTime 等类型
 * 必须通过 @TableField(typeHandler = JsonTypeHandler.class) 明确指定
 *
 * @param <T> JSON 对应的 Java 类型
 */
@Slf4j
public class JsonTypeHandler<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Class<T> type;

    /**
     * 无参构造函数（MyBatis-Plus 需要）
     */
    @SuppressWarnings("unchecked")
    public JsonTypeHandler() {
        this.type = (Class<T>) Object.class;
    }

    public JsonTypeHandler(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (JacksonException e) {
            log.error("Error serializing JSON parameter: {}", parameter, e);
            throw new SQLException("Error serializing JSON parameter", e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private T parseJson(String json) throws SQLException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            log.error("Error deserializing JSON: {}", json, e);
            throw new SQLException("Error deserializing JSON", e);
        }
    }
}
