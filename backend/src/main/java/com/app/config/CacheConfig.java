package com.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        // Default settings align with application.yml (maximumSize=500, expireAfterWrite=4h),
        // but we override TTL for the todo widget per spec.
        Caffeine<Object, Object> defaultCaffeine =
                Caffeine.newBuilder().maximumSize(500).expireAfterWrite(4, TimeUnit.HOURS);

        Caffeine<Object, Object> todoDashboard =
                Caffeine.newBuilder().maximumSize(500).expireAfterWrite(2, TimeUnit.MINUTES);
        Caffeine<Object, Object> todoCounts =
                Caffeine.newBuilder().maximumSize(500).expireAfterWrite(5, TimeUnit.MINUTES);

        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(
                new CaffeineCache("fx_taux_du_jour", defaultCaffeine.build()),
                new CaffeineCache("fx_tous_taux_du_jour", defaultCaffeine.build()),
                new CaffeineCache("todo-dashboard", todoDashboard.build()),
                new CaffeineCache("todo-dashboard-counts", todoCounts.build())
        ));
        return mgr;
    }
}

