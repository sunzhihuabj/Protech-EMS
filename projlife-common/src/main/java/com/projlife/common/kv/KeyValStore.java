package com.projlife.common.kv;

/**
 * KV 存储抽象（ADR-005）：登录失败计数、权限缓存、token 黑名单等使用。
 * 生产 = Redis；CI/离线 = 内存实现。业务代码只依赖本接口。
 */
public interface KeyValStore {

    /**
     * 原子自增并设置过期（首次写入时设置 TTL）。返回自增后的值。
     */
    long increment(String key, long ttlSeconds);

    /** 读取（long）；键不存在返回 -1 */
    long getLong(String key);

    /** 写字符串值（带 TTL） */
    void put(String key, String value, long ttlSeconds);

    /** 读字符串值；不存在返回 null */
    String get(String key);

    /** 删除键 */
    void remove(String key);

    /** 键是否存在 */
    boolean exists(String key);
}
