package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.jrfind.domain.Station;

// 게시판 목록의 조건을 유지한 주소, 페이지 번호 목록
class BoardQueryTest {

	@Test
	void 페이지_번호는_처음_끝과_지금_페이지_앞뒤_2쪽만() {
		assertThat(BoardQuery.pageNumbers(1, 1)).containsExactly(1);
		assertThat(BoardQuery.pageNumbers(3, 5)).containsExactly(1, 2, 3, 4, 5);
		assertThat(BoardQuery.pageNumbers(10, 20)).containsExactly(1, null, 8, 9, 10, 11, 12, null, 20);
		assertThat(BoardQuery.pageNumbers(1, 669)).containsExactly(1, 2, 3, null, 669); // 글이 1만 건이어도 7개 이하
		assertThat(BoardQuery.pageNumbers(4, 7)).containsExactly(1, 2, 3, 4, 5, 6, 7); // 하나만 건너뛰는 곳은 「…」 대신 번호
		assertThat(BoardQuery.pageNumbers(1, 0)).isEmpty();
	}

	@Test
	void 조건을_유지한_주소를_만든다() {
		BoardQuery query = new BoardQuery("/items", "山手線", "", null, "C++ & 財布", "date_asc");

		assertThat(query.pageUrl(2)).isEqualTo("/items?line=%E5%B1%B1%E6%89%8B%E7%B7%9A&q=C%2B%2B+%26+%E8%B2%A1%E5%B8%83&sort=date_asc&page=2");
		// 같은 열을 다시 누르면 반대로, 다른 열은 내림차순부터. 1페이지로
		assertThat(query.sortUrl("date")).endsWith("&sort=date_desc");
		assertThat(query.sortUrl("reg")).doesNotContain("sort=").doesNotContain("page="); // 기본값(작성일 최신순)은 생략
		assertThat(query.withoutKeywordUrl()).isEqualTo("/items?line=%E5%B1%B1%E6%89%8B%E7%B7%9A&sort=date_asc");
		assertThat(query.ariaSort("date")).isEqualTo("ascending");
		assertThat(query.ariaSort("reg")).isNull();

		// 없는 정렬 값·latest 는 작성일 최신순
		assertThat(new BoardQuery("/items", null, null, null, null, "latest").sort()).isEqualTo("reg_desc");
		assertThat(new BoardQuery("/items", null, null, null, null, "x' OR 1=1").sort()).isEqualTo("reg_desc");
		assertThat(new BoardQuery("/items", null, null, null, null, null).isFiltered()).isFalse();
	}

	@Test
	void 상세_화면의_一覧은_목록에서_왔으면_그_목록으로() {
		assertThat(BoardQuery.listUrlFrom(withReferer("http://localhost/items?line=%E5%B1%B1&page=2"), "/items"))
			.isEqualTo("/items?line=%E5%B1%B1&page=2");
		assertThat(BoardQuery.listUrlFrom(withReferer(null), "/items")).isEqualTo("/items");
		assertThat(BoardQuery.listUrlFrom(withReferer("http://localhost/search?q=x"), "/items")).isEqualTo("/items");
		assertThat(BoardQuery.listUrlFrom(withReferer("https://evil.example/items?x"), "/items")).isEqualTo("/items");
		assertThat(BoardQuery.listUrlFrom(withReferer("http://localhost/lost-items?page=3"), "/items")).isEqualTo("/items");
	}

	@Test
	void 역마다_지나는_노선을_모은다() {
		List<Station> stations = Arrays.asList(station("山手線", "東京"), station("京浜東北線", "東京"), station("山手線", "神田"));
		assertThat(BoardQuery.stationLines(stations)).containsExactly(
			org.assertj.core.api.Assertions.entry("東京", "山手線,京浜東北線"),
			org.assertj.core.api.Assertions.entry("神田", "山手線"));
	}

	private MockHttpServletRequest withReferer(String referer) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/items/1");
		request.setServerName("localhost");
		if (referer != null) {
			request.addHeader("Referer", referer);
		}
		return request;
	}

	private Station station(String line, String name) {
		Station station = new Station();
		station.setLineName(line);
		station.setStationName(name);
		return station;
	}
}
