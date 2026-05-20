package com.eify.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisUtil")
class RedisUtilTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    ValueOperations<String, Object> valueOps;

    @Mock
    HashOperations<String, Object, Object> hashOps;

    @Mock
    ListOperations<String, Object> listOps;

    @Mock
    SetOperations<String, Object> setOps;

    @Mock
    ZSetOperations<String, Object> zSetOps;

    @InjectMocks
    RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    // ==================== String 操作 ====================

    @Nested
    @DisplayName("String 操作")
    class StringOps {

        @Test
        @DisplayName("set 应委托给 opsForValue")
        void shouldSetValue() {
            redisUtil.set("key", "value");
            verify(valueOps).set("key", "value");
        }

        @Test
        @DisplayName("set 带超时和单位应委托给 opsForValue")
        void shouldSetWithTimeoutAndUnit() {
            redisUtil.set("key", "value", 10, java.util.concurrent.TimeUnit.MINUTES);
            verify(valueOps).set("key", "value", 10, java.util.concurrent.TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("set 带超时秒数应默认使用秒单位")
        void shouldSetWithTimeoutInSeconds() {
            redisUtil.set("key", "value", 30);
            verify(valueOps).set("key", "value", 30, java.util.concurrent.TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("get 应返回存储的值")
        void shouldGetValue() {
            when(valueOps.get("key")).thenReturn("value");

            Object result = redisUtil.get("key");

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("get 带类型应返回对应类型")
        void shouldGetTypedValue() {
            when(valueOps.get("key")).thenReturn("value");

            String result = redisUtil.get("key", String.class);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("get 不存在应返回 null")
        void shouldReturnNullWhenKeyMissing() {
            when(valueOps.get("nonexistent")).thenReturn(null);

            String result = redisUtil.get("nonexistent", String.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("delete 单 key 应委托给 redisTemplate")
        void shouldDeleteSingle() {
            when(redisTemplate.delete("key")).thenReturn(true);

            Boolean result = redisUtil.delete("key");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("delete 批量应委托给 redisTemplate")
        void shouldDeleteBatch() {
            List<String> keys = List.of("k1", "k2");
            when(redisTemplate.delete(keys)).thenReturn(2L);

            Long result = redisUtil.delete(keys);

            assertThat(result).isEqualTo(2L);
        }

        @Test
        @DisplayName("hasKey 应委托给 redisTemplate")
        void shouldCheckKeyExists() {
            when(redisTemplate.hasKey("key")).thenReturn(true);

            assertThat(redisUtil.hasKey("key")).isTrue();
        }
    }

    // ==================== Hash 操作 ====================

    @Nested
    @DisplayName("Hash 操作")
    class HashOps {

        @Test
        @DisplayName("hSet 应委托给 opsForHash")
        void shouldSetHash() {
            redisUtil.hSet("key", "hk", "val");
            verify(hashOps).put("key", "hk", "val");
        }

        @Test
        @DisplayName("hGet 应返回 hash 值")
        void shouldGetHash() {
            when(hashOps.get("key", "hk")).thenReturn("val");

            Object result = redisUtil.hGet("key", "hk");

            assertThat(result).isEqualTo("val");
        }

        @Test
        @DisplayName("hGetAll 应返回全部 hash 条目")
        void shouldGetAllHashEntries() {
            Map<Object, Object> entries = Map.of("hk1", "v1", "hk2", "v2");
            when(hashOps.entries("key")).thenReturn(entries);

            Map<Object, Object> result = redisUtil.hGetAll("key");

            assertThat(result).hasSize(2);
        }
    }

    // ==================== List 操作 ====================

    @Nested
    @DisplayName("List 操作")
    class ListOps {

        @Test
        @DisplayName("lLeftPush 应委托给 opsForList")
        void shouldLeftPush() {
            when(listOps.leftPush("key", "val")).thenReturn(1L);

            Long result = redisUtil.lLeftPush("key", "val");

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("lRange 无参数应取全部")
        void shouldRangeAll() {
            List<Object> expected = List.of("a", "b");
            when(listOps.range("key", 0, -1)).thenReturn(expected);

            List<Object> result = redisUtil.lRange("key");

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("lSize 应委托给 opsForList")
        void shouldGetSize() {
            when(listOps.size("key")).thenReturn(5L);

            assertThat(redisUtil.lSize("key")).isEqualTo(5L);
        }

        @Test
        @DisplayName("lLeftPop 应弹出左侧元素")
        void shouldLeftPop() {
            when(listOps.leftPop("key")).thenReturn("val");

            assertThat(redisUtil.lLeftPop("key")).isEqualTo("val");
        }
    }

    // ==================== Set 操作 ====================

    @Nested
    @DisplayName("Set 操作")
    class SetOps {

        @Test
        @DisplayName("sAdd 应委托给 opsForSet")
        void shouldAddToSet() {
            when(setOps.add(anyString(), any(Object[].class))).thenReturn(2L);

            Long result = redisUtil.sAdd("key", "a", "b");

            assertThat(result).isEqualTo(2L);
        }

        @Test
        @DisplayName("sMembers 应返回所有成员")
        void shouldGetMembers() {
            Set<Object> members = Set.of("a", "b");
            when(setOps.members("key")).thenReturn(members);

            assertThat(redisUtil.sMembers("key")).hasSize(2);
        }
    }

    // ==================== ZSet 操作 ====================

    @Nested
    @DisplayName("ZSet 操作")
    class ZSetOps {

        @Test
        @DisplayName("zAdd 应委托给 opsForZSet")
        void shouldAddToZSet() {
            when(zSetOps.add("key", "val", 1.0)).thenReturn(true);

            assertThat(redisUtil.zAdd("key", "val", 1.0)).isTrue();
        }

        @Test
        @DisplayName("zRange 升序应委托给 opsForZSet")
        void shouldRangeAsc() {
            Set<Object> expected = new LinkedHashSet<>(List.of("a", "b"));
            when(zSetOps.range("key", 0, 1)).thenReturn(expected);

            assertThat(redisUtil.zRange("key", 0, 1)).hasSize(2);
        }
    }

    // ==================== 通用 ====================

    @Nested
    @DisplayName("通用操作")
    class CommonOps {

        @Test
        @DisplayName("keys 应返回匹配的 key 集合")
        void shouldReturnMatchingKeys() {
            Set<String> keys = Set.of("user:1", "user:2");
            when(redisTemplate.keys("user:*")).thenReturn(keys);

            assertThat(redisUtil.keys("user:*")).hasSize(2);
        }

        @Test
        @DisplayName("flushAll 应删除所有 key")
        void shouldDeleteAllKeys() {
            Set<String> keys = Set.of("k1", "k2");
            when(redisTemplate.keys("*")).thenReturn(keys);
            when(redisTemplate.delete(keys)).thenReturn(2L);

            redisUtil.flushAll();

            verify(redisTemplate).keys("*");
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("flushAll 无 key 时不应调用 delete")
        void shouldNotDeleteWhenNoKeys() {
            when(redisTemplate.keys("*")).thenReturn(Collections.emptySet());

            redisUtil.flushAll();

            verify(redisTemplate, never()).delete(anySet());
        }
    }
}
