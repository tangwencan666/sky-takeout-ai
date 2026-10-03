package com.tang.sky.config;

import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/** 放在事务装饰器内部，才能捕获afterCommit阶段的缓存写/失效故障。数据库仍是事实来源。 */
public final class BestEffortCache implements Cache {
    private static final Logger log=LoggerFactory.getLogger(BestEffortCache.class);
    private final Cache delegate;
    public BestEffortCache(Cache delegate) { this.delegate=delegate; }
    public String getName() { return delegate.getName(); }
    public Object getNativeCache() { return delegate.getNativeCache(); }
    public ValueWrapper get(Object key) { return delegate.get(key); }
    public <T> T get(Object key,Class<T> type) { return delegate.get(key,type); }
    public <T> T get(Object key,Callable<T> loader) { return delegate.get(key,loader); }
    public void put(Object key,Object value) { attempt(()->delegate.put(key,value),"put"); }
    public void evict(Object key) { attempt(()->delegate.evict(key),"evict"); }
    public void clear() { attempt(delegate::clear,"clear"); }
    private void attempt(Runnable action,String operation) {
        try { action.run(); }
        catch(RuntimeException e) {
            // 不记录key、值或异常原文；菜单TTL限制故障期间陈旧数据的生存时间。
            log.warn("Cache mutation unavailable cache={} operation={}",getName(),operation);
        }
    }
}
