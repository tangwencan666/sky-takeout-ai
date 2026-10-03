package com.tang.sky.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tang.sky.pojo.CatalogModels.*;
import java.time.Duration;
import java.util.List;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.*;
import org.springframework.cache.annotation.*;
import org.springframework.cache.interceptor.*;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;
/** Cache Aside：先读缓存，缺失查DB；提交后失效，TTL限制异常情况下的陈旧时间。 */
@Configuration @EnableCaching @ConditionalOnProperty(name="sky.cache.enabled",havingValue="true")
public class CacheConfig implements CachingConfigurer {
    private static final Logger log=LoggerFactory.getLogger(CacheConfig.class);
    @Bean public RedisCacheManager redisCacheManager(RedisConnectionFactory factory,ObjectMapper json){
        var categories=new Jackson2JsonRedisSerializer<>(json,json.getTypeFactory().constructCollectionType(List.class,Category.class));
        var pages=new Jackson2JsonRedisSerializer<>(json,json.getTypeFactory().constructParametricType(PageView.class,Dish.class));
        var defaults=RedisCacheConfiguration.defaultCacheConfig().computePrefixWith(name->"sky:menu:v1:"+name+":")
                .disableCachingNullValues().entryTtl(Duration.ofSeconds(120));
        // 指定JSON目标类型，避免接收缓存中的任意Java类型；SCAN仅清理本项目命名空间。
        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(factory,BatchStrategies.scan(100)))
                .withCacheConfiguration("categories",defaults.entryTtl(Duration.ofMinutes(10)).serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(categories)))
                .withCacheConfiguration("dishes",defaults.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(pages)))
                .enableStatistics().build();
    }
    @Bean @Primary public CacheManager cacheManager(RedisCacheManager redis){
        var safe=new CacheManager(){
            private final java.util.Map<String,Cache> wrappers=new java.util.concurrent.ConcurrentHashMap<>();
            public Cache getCache(String name){return wrappers.computeIfAbsent(name,n->{var cache=redis.getCache(n);return cache==null?null:new BestEffortCache(cache);});}
            public java.util.Collection<String> getCacheNames(){return redis.getCacheNames();}
        };
        return new org.springframework.cache.transaction.TransactionAwareCacheManagerProxy(safe);
    }
    @Override public CacheErrorHandler errorHandler(){return new CacheErrorHandler(){
        public void handleCacheGetError(RuntimeException e,Cache c,Object k){log.warn("Cache read unavailable cache={}",c.getName());}
        public void handleCachePutError(RuntimeException e,Cache c,Object k,Object v){log.warn("Cache write unavailable cache={}",c.getName());}
        public void handleCacheEvictError(RuntimeException e,Cache c,Object k){log.warn("Cache eviction unavailable cache={}",c.getName());}
        public void handleCacheClearError(RuntimeException e,Cache c){log.warn("Cache clear unavailable cache={}",c.getName());}
    };}
}
