package com.eify.knowledge.config;

import com.pgvector.PGvector;

import org.springframework.stereotype.Component;

/**
 * pgvector 向量类型转换器
 * <p>
 * JdbcTemplate 使用时手动调用 toVector() / fromVector()。
 */
@Component
public class VectorTypeHandler {

    /**
     * float[] → PGvector（用于 PreparedStatement.setObject）
     */
    public PGvector toVector(float[] array) {
        if (array == null) return null;
        return new PGvector(array);
    }

    /**
     * PGvector → float[]（用于 ResultSet 读取）
     */
    public float[] fromVector(Object obj) {
        if (obj == null) return null;
        if (obj instanceof PGvector pgv) {
            return pgv.toArray();
        }
        // 从字符串格式 "[0.1,0.2,...]" 解析
        String str = obj.toString().replaceAll("[\\[\\]\\s]", "");
        String[] parts = str.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    /**
     * float[] → pgvector 字面量字符串 '[0.1,0.2,...]'
     * 用于 SQL 中的 ::vector cast
     */
    public String toVectorLiteral(float[] array) {
        if (array == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        return sb.append("]").toString();
    }
}
