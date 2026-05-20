package com.eify.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // ============================ String ============================

    /**
     * 设置缓存
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置缓存（带过期时间）
     *
     * @param key   键
     * @param value 值
     * @param timeout 过期时间
     * @param unit  时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 设置缓存（带过期时间-秒）
     *
     * @param key   键
     * @param value 值
     * @param timeout 过期时间（秒）
     */
    public void set(String key, Object value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取缓存（指定类型）
     *
     * @param key 键
     * @param clazz 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? (T) value : null;
    }

    /**
     * 删除缓存
     *
     * @param key 键
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存
     *
     * @param keys 键集合
     */
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return true=存在，false=不存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置过期时间
     *
     * @param key    键
     * @param timeout 过期时间
     * @param unit   时间单位
     * @return true=成功，false=失败
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 设置过期时间（秒）
     *
     * @param key    键
     * @param timeout 过期时间（秒）
     * @return true=成功，false=失败
     */
    public Boolean expire(String key, long timeout) {
        return redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 获取过期时间
     *
     * @param key 键
     * @return 过期时间（秒），-1=永不过期，-2=key 不存在
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 获取过期时间（指定时间单位）
     *
     * @param key  键
     * @param unit 时间单位
     * @return 过期时间
     */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    // ============================ Hash ============================

    /**
     * 设置 Hash 缓存
     *
     * @param key     键
     * @param hashKey Hash 键
     * @param value   值
     */
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * 设置 Hash 缓存（带过期时间）
     *
     * @param key     键
     * @param hashKey Hash 键
     * @param value   值
     * @param timeout 过期时间（秒）
     */
    public void hSet(String key, String hashKey, Object value, long timeout) {
        redisTemplate.opsForHash().put(key, hashKey, value);
        expire(key, timeout);
    }

    /**
     * 获取 Hash 缓存
     *
     * @param key     键
     * @param hashKey Hash 键
     * @return 值
     */
    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 获取 Hash 缓存（指定类型）
     *
     * @param key     键
     * @param hashKey Hash 键
     * @param clazz   类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String hashKey, Class<T> clazz) {
        Object value = redisTemplate.opsForHash().get(key, hashKey);
        return value != null ? (T) value : null;
    }

    /**
     * 获取所有 Hash 值
     *
     * @param key 键
     * @return Map
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 删除 Hash 缓存
     *
     * @param key     键
     * @param hashKeys Hash 键集合
     * @return 删除数量
     */
    public Long hDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * 判断 Hash 字段是否存在
     *
     * @param key     键
     * @param hashKey Hash 键
     * @return true=存在，false=不存在
     */
    public Boolean hHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    // ============================ List ============================

    /**
     * 设置 List 缓存（左侧插入）
     *
     * @param key   键
     * @param value 值
     * @return 插入后列表长度
     */
    public Long lLeftPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 设置 List 缓存（右侧插入）
     *
     * @param key   键
     * @param value 值
     * @return 插入后列表长度
     */
    public Long lRightPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 获取 List 缓存（指定范围）
     *
     * @param key   键
     * @param start 开始位置
     * @param end   结束位置
     * @return List
     */
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 获取 List 缓存（全部）
     *
     * @param key 键
     * @return List
     */
    public List<Object> lRange(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * 获取 List 长度
     *
     * @param key 键
     * @return 长度
     */
    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * 移除 List 元素（左侧）
     *
     * @param key 键
     * @return 移除的元素
     */
    public Object lLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 移除 List 元素（右侧）
     *
     * @param key 键
     * @return 移除的元素
     */
    public Object lRightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    // ============================ Set ============================

    /**
     * 设置 Set 缓存
     *
     * @param key    键
     * @param values 值集合
     * @return 添加数量
     */
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * 获取 Set 所有成员
     *
     * @param key 键
     * @return Set
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 判断 Set 成员是否存在
     *
     * @param key   键
     * @param value 值
     * @return true=存在，false=不存在
     */
    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * 获取 Set 长度
     *
     * @param key 键
     * @return 长度
     */
    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * 删除 Set 成员
     *
     * @param key    键
     * @param values 值集合
     * @return 删除数量
     */
    public Long sRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    // ============================ ZSet ============================

    /**
     * 设置 ZSet 缓存
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     * @return true=成功，false=失败
     */
    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * 获取 ZSet 指定范围成员（升序）
     *
     * @param key   键
     * @param start 开始位置
     * @param end   结束位置
     * @return Set
     */
    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * 获取 ZSet 指定范围成员（降序）
     *
     * @param key   键
     * @param start 开始位置
     * @param end   结束位置
     * @return Set
     */
    public Set<Object> zReverseRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * 获取 ZSet 成员分数
     *
     * @param key   键
     * @param value 值
     * @return 分数
     */
    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    /**
     * 获取 ZSet 长度
     *
     * @param key 键
     * @return 长度
     */
    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * 删除 ZSet 成员
     *
     * @param key    键
     * @param values 值集合
     * @return 删除数量
     */
    public Long zRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    // ============================ 通用 ============================

    /**
     * 获取所有匹配的 key
     *
     * @param pattern 匹配模式（如：user:*）
     * @return key 集合
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 清空所有缓存
     */
    public void flushAll() {
        Set<String> keys = keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
