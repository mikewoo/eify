package com.eify.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseEntity")
class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    @Nested
    @DisplayName("getters and setters")
    class GettersAndSetters {

        @Test
        @DisplayName("id set/get")
        void shouldSetAndGetId() {
            TestEntity entity = new TestEntity();
            entity.setId(42L);

            assertThat(entity.getId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("createdAt set/get")
        void shouldSetAndGetCreatedAt() {
            TestEntity entity = new TestEntity();
            LocalDateTime now = LocalDateTime.of(2026, 5, 15, 10, 30);
            entity.setCreatedAt(now);

            assertThat(entity.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("updatedAt set/get")
        void shouldSetAndGetUpdatedAt() {
            TestEntity entity = new TestEntity();
            LocalDateTime now = LocalDateTime.of(2026, 5, 15, 10, 30);
            entity.setUpdatedAt(now);

            assertThat(entity.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("deleted set/get")
        void shouldSetAndGetDeleted() {
            TestEntity entity = new TestEntity();
            entity.setDeleted(1);

            assertThat(entity.getDeleted()).isEqualTo(1);
        }

        @Test
        @DisplayName("creatorId set/get")
        void shouldSetAndGetCreatorId() {
            TestEntity entity = new TestEntity();
            entity.setCreatorId(100L);

            assertThat(entity.getCreatorId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("default values")
    class DefaultValues {

        @Test
        @DisplayName("新建实体各字段默认为 null")
        void shouldHaveNullDefaults() {
            TestEntity entity = new TestEntity();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
            assertThat(entity.getDeleted()).isNull();
            assertThat(entity.getCreatorId()).isNull();
        }
    }

    @Nested
    @DisplayName("serialization")
    class Serialization {

        @Test
        @DisplayName("BaseEntity 实现 Serializable")
        void shouldImplementSerializable() {
            TestEntity entity = new TestEntity();
            assertThat(entity).isInstanceOf(java.io.Serializable.class);
        }
    }
}
