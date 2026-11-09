package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

// 駅マップ: 역 위치·노선도 데이터, 역별 게시글 수, お忘れ物 문의처
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MapControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 駅マップは_로그인_없이_볼_수_있고_헤더와_홈에서_연결된다() throws Exception {
		mockMvc.perform(get("/map"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"map\"")))
			.andExpect(content().string(containsString("050-2016-1601")))
			// 지도 라이브러리는 외부 CDN 이 아니라 이 앱에서 제공한다 (WebJars. CDN 이 막혀도 지도가 동작하도록)
			// (화면에는 버전이 붙은 주소로 나간다: /webjars/leaflet/1.9.4/dist/leaflet.js)
			.andExpect(content().string(containsString("src=\"/webjars/leaflet/")))
			.andExpect(content().string(not(containsString("cdn.jsdelivr.net"))));
		mockMvc.perform(get("/webjars/leaflet/dist/leaflet.js")).andExpect(status().isOk());
		mockMvc.perform(get("/webjars/leaflet/dist/leaflet.css")).andExpect(status().isOk());

		mockMvc.perform(get("/items"))
			.andExpect(content().string(containsString("href=\"/map\" class=\"header-svg\"")));
		mockMvc.perform(get("/"))
			.andExpect(content().string(containsString("href=\"/map\" class=\"nav-icon\"")));
	}

	@Test
	void 노선은_등록된_역_순서대로_내려준다() throws Exception {
		mockMvc.perform(get("/map/stations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lines.length()").value(6))
			.andExpect(jsonPath("$.lines[0].name").value("山手線"))
			.andExpect(jsonPath("$.lines[0].stations.length()").value(18))
			.andExpect(jsonPath("$.lines[0].stations[0]").value("東京"))
			.andExpect(jsonPath("$.lines[0].stations[17]").value("新橋"));
	}

	@Test
	void 모든_역에_위치가_있고_지나는_노선과_お忘れ物承り所_여부를_내려준다() throws Exception {
		String json = mockMvc.perform(get("/map/stations")).andReturn().getResponse().getContentAsString();

		List<String> names = JsonPath.read(json, "$.stations[*].name");
		int distinctStations = jdbcTemplate.queryForObject("SELECT count(DISTINCT STATIONNAME) FROM STATION", Integer.class);
		assertThat(names).hasSize(distinctStations).doesNotHaveDuplicates();

		// 도쿄 근교 범위 안의 좌표
		List<Double> lats = JsonPath.read(json, "$.stations[*].lat");
		List<Double> lngs = JsonPath.read(json, "$.stations[*].lng");
		assertThat(lats).allMatch(lat -> lat > 35.0 && lat < 36.0);
		assertThat(lngs).allMatch(lng -> lng > 139.0 && lng < 140.2);

		List<List<String>> shinjukuLines = JsonPath.read(json, "$.stations[?(@.name == '新宿')].lines");
		assertThat(shinjukuLines.get(0)).containsExactlyInAnyOrder("山手線", "中央・総武緩行線", "埼京線");

		List<String> lostCenters = JsonPath.read(json, "$.stations[?(@.lostCenter == true)].name");
		assertThat(lostCenters).containsExactlyInAnyOrder("東京", "大宮");
	}

	@Test
	void 역별_게시글_수는_삭제된_글과_역을_모르는_글을_빼고_센다() throws Exception {
		int foundBefore = count("foundCount", "高円寺");
		int lostBefore = count("lostCount", "高円寺");

		insertFound("高円寺", false);
		insertFound("高円寺", false);
		insertFound("高円寺", true); // 삭제된 글
		insertLost("高円寺");
		insertLost(null); // 역을 모르는 글

		assertThat(count("foundCount", "高円寺")).isEqualTo(foundBefore + 2);
		assertThat(count("lostCount", "高円寺")).isEqualTo(lostBefore + 1);
	}

	@Test
	void 상세_화면에는_확인된_お忘れ物_문의처만_보여준다() throws Exception {
		insertFound("新宿", false);
		long foundId = jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);

		mockMvc.perform(get("/items/{id}", foundId))
			.andExpect(content().string(containsString("050-2016-1601")))
			.andExpect(content().string(containsString("https://www.jreast.co.jp/info/lost.html")))
			.andExpect(content().string(not(containsString("xxxx")))); // 예전의 임시 번호
		insertLost("新宿");
		long lostId = jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);
		mockMvc.perform(get("/lost-items/{id}", lostId))
			.andExpect(content().string(containsString("050-2016-1601")));
	}

	// ---------- 도우미 ----------

	private int count(String field, String station) throws Exception {
		String json = mockMvc.perform(get("/map/stations")).andReturn().getResponse().getContentAsString();
		List<Integer> values = JsonPath.read(json, "$.stations[?(@.name == '" + station + "')]." + field);
		return values.get(0);
	}

	private void insertFound(String station, boolean deleted) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, DEL_FLG, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-08', '中央・総武緩行線', ?, 'その他', '傘', '内容', '匿名の天使 001', ?, ?)",
			"[2026-01-08][中央・総武緩行線][" + station + "][その他] 傘", station, deleted, LocalDateTime.now());
	}

	private void insertLost(String station) {
		Long memberId = jdbcTemplate.queryForObject("SELECT min(ID) FROM MEMBER", Long.class);
		if (memberId == null) {
			jdbcTemplate.update("INSERT INTO MEMBER (LOGINID, PASSWORD, REALNAME, NICKNAME, BIRTHDATE, PHONE, AGREEDDATE)"
				+ " VALUES ('map_member', 'x', '地図太郎', '地図', '2000-01-01', '090-0000-0000', CURRENT_TIMESTAMP)");
			memberId = jdbcTemplate.queryForObject("SELECT min(ID) FROM MEMBER", Long.class);
		}
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-12][中央・総武緩行線][高円寺][その他] 傘', '2026-01-12', '中央・総武緩行線', ?, 'その他', '傘', '内容', '地図', ?, ?)",
			station, memberId, LocalDateTime.now());
	}
}
