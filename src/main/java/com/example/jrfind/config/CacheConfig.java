package com.example.jrfind.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 노선·역·카테고리·역 위치(마스터 데이터)는 서버를 켤 때 data.sql 로만 넣고 실행 중에는 바뀌지 않으므로,
// 처음 한 번 읽은 것을 메모리에 두고 쓴다 (목록·작성 화면을 열 때마다 DB 를 읽지 않도록). JdbcJrfindRepository 의 @Cacheable
@Configuration
@EnableCaching
public class CacheConfig {
	public static final String MASTER_DATA = "masterData";

	@Bean
	public CacheManager cacheManager() {
		return new ConcurrentMapCacheManager(MASTER_DATA);
	}
}
