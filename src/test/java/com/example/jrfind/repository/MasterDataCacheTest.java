package com.example.jrfind.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 마스터 데이터(노선·역·카테고리·역 위치)는 처음 한 번 읽은 것을 계속 쓴다
@SpringBootTest
@ActiveProfiles("test")
class MasterDataCacheTest {
	@Autowired
	private JrfindRepository repository;

	@Test
	void 두_번째부터는_DB_를_읽지_않고_같은_목록을_돌려준다() {
		assertThat(repository.findAllCategories()).isNotEmpty().isSameAs(repository.findAllCategories());
		assertThat(repository.findAllLines()).isNotEmpty().isSameAs(repository.findAllLines());
		assertThat(repository.findAllStations()).isNotEmpty().isSameAs(repository.findAllStations());
		assertThat(repository.findAllStationLocations()).isNotEmpty().isSameAs(repository.findAllStationLocations());
	}
}
