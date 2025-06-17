package com.doreamr233.charartconverter.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * <p>
 * 该类负责配置Redis连接和序列化方式，用于缓存字符画文本数据。
 * 通过配置RedisTemplate来实现对Redis的操作，并设置缓存的过期时间。
 * </p>
 *
 * @author doreamr233
 */
@Getter
@Configuration
public class RedisConfig {

    /**
     * 缓存过期时间（秒）
     * <p>
     * 从配置文件中读取缓存过期时间，默认为3600秒（1小时）
     * </p>
     * -- GETTER --
     *  获取缓存过期时间（秒）
     *  <p>
     *  提供缓存过期时间的访问方法，供其他组件使用。
     *  </p>
     *
     */
    @Value("${char-art.cache.ttl:3600}")
    private long cacheTimeToLive;

    /**
     * Redis缓存键前缀
     * 用于区分不同类型的缓存数据，这里特定用于字符画文本缓存
     */
    @Value("${char-art.cache.default_key_prefix}")
    public static final String CACHE_KEY_PREFIX = "char-art:text:";

    /**
     * 配置RedisTemplate
     * <p>
     * 创建并配置RedisTemplate实例，用于操作Redis数据库。
     * 设置键、值、哈希键、哈希值的序列化方式为StringRedisSerializer，
     * 以便于存储和读取字符串类型的数据。
     * </p>
     *
     * @param connectionFactory Redis连接工厂
     * @return 配置好的RedisTemplate实例
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

}