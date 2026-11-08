package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 검색: 두 게시판의 제목·본문, 공백으로 나눈 단어는 모두 포함, 대소문자 무시, % _ 는 글자 그대로
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	// 테스트마다 다른 단어를 넣어서 다른 테스트의 글과 섞이지 않게 한다
	private final String marker = "srch" + UUID.randomUUID().toString().substring(0, 8);

	// ---------- 통합 검색 ----------

	@Test
	void 통합_검색은_두_게시판에서_제목이나_본문에_검색어가_있는_글을_찾는다() throws Exception {
		long inContents = insertFound("青い傘", "新宿", "改札で拾いました。" + marker, false);
		long inTitle = insertFound("ハンカチ " + marker, "渋谷", "ホームのベンチ", false);
		long deleted = insertFound("財布", "新宿", marker, true);
		long other = insertFound("帽子", "新宿", "関係ない投稿", false);
		long lost = insertLost("イヤホン", null, "片耳だけ落としました " + marker);

		Map<String, Object> model = mockMvc.perform(get("/search").param("q", marker))
			.andExpect(status().isOk())
			.andExpect(model().attribute("foundCount", 2))
			.andExpect(model().attribute("lostCount", 1))
			.andExpect(content().string(containsString("href=\"/items?q=" + marker + "\"")))
			.andExpect(content().string(containsString("href=\"/lost-items?q=" + marker + "\"")))
			.andReturn().getModelAndView().getModel();

		assertThat(foundIds(model.get("foundItems"))).containsExactlyInAnyOrder(inContents, inTitle)
			.doesNotContain(deleted, other);
		assertThat(lostIds(model.get("lostItems"))).containsExactly(lost);
	}

	@Test
	void 통합_검색은_게시판마다_최신_글_5건까지만_보여준다() throws Exception {
		for (int i = 0; i < 7; i++) {
			insertFound("傘" + i, "新宿", marker, false);
		}

		Map<String, Object> model = mockMvc.perform(get("/search").param("q", marker))
			.andExpect(model().attribute("foundCount", 7))
			.andExpect(model().attribute("lostCount", 0))
			.andExpect(content().string(containsString("見つかった投稿はありません。"))) // 探しています 쪽
			.andReturn().getModelAndView().getModel();

		assertThat(foundItems(model.get("foundItems"))).extracting(FoundItem::getItemName)
			.containsExactly("傘6", "傘5", "傘4", "傘3", "傘2");
	}

	@Test
	void 공백으로_나눈_단어를_모두_포함하는_글만_찾는다() throws Exception {
		long shinjuku = insertFound("黒い財布", "新宿", marker + " 南口で拾いました", false);
		insertFound("黒い財布", "渋谷", marker + " ハチ公前で拾いました", false);

		// 전각 공백으로 나눠도 된다
		Map<String, Object> model = mockMvc.perform(get("/search").param("q", marker + "　新宿  財布"))
			.andExpect(model().attribute("foundCount", 1))
			.andReturn().getModelAndView().getModel();
		assertThat(foundIds(model.get("foundItems"))).containsExactly(shinjuku);

		mockMvc.perform(get("/search").param("q", marker + " 新宿 ハチ公"))
			.andExpect(model().attribute("foundCount", 0));
	}

	@Test
	void 대소문자를_구분하지_않는다() throws Exception {
		insertFound("iPhone 15", "新宿", marker, false);

		mockMvc.perform(get("/search").param("q", marker.toUpperCase() + " IPHONE"))
			.andExpect(model().attribute("foundCount", 1));
	}

	@Test
	void 퍼센트와_밑줄은_와일드카드가_아니라_글자_그대로_찾는다() throws Exception {
		insertFound("クーポン", "新宿", marker + " 100%OFF", false);
		insertFound("クーポン", "新宿", marker + " 1000円OFF", false);

		mockMvc.perform(get("/search").param("q", marker + " 100%"))
			.andExpect(model().attribute("foundCount", 1));
		mockMvc.perform(get("/search").param("q", marker + " _"))
			.andExpect(model().attribute("foundCount", 0));
	}

	@Test
	void 검색어가_없으면_입력_안내만_보여준다() throws Exception {
		mockMvc.perform(get("/search").param("q", " 　 "))
			.andExpect(status().isOk())
			.andExpect(model().attribute("keyword", (Object) null))
			.andExpect(model().attributeDoesNotExist("foundItems", "lostItems"))
			.andExpect(content().string(containsString("キーワードを入力してください。")));
		mockMvc.perform(get("/search"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("キーワードを入力してください。")));
	}

	@Test
	void 검색어는_HTML이나_스크립트로_해석되지_않는다() throws Exception {
		String keyword = "<script>alert(1)</script>";

		mockMvc.perform(get("/search").param("q", keyword))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString(keyword))));
		mockMvc.perform(get("/items").param("q", keyword))
			.andExpect(content().string(not(containsString(keyword))));
		mockMvc.perform(get("/lost-items").param("q", keyword))
			.andExpect(content().string(not(containsString(keyword))));
	}

	// ---------- 게시판 목록 ----------

	@Test
	@SuppressWarnings("unchecked")
	void 게시판_목록에서_검색어와_필터를_함께_쓸_수_있다() throws Exception {
		long shinjuku = insertFound("傘", "新宿", marker, false);
		insertFound("傘", "渋谷", marker, false);
		long lost = insertLost("傘", "新宿", marker);

		mockMvc.perform(get("/items").param("q", marker))
			.andExpect(model().attribute("totalItems", 2))
			.andExpect(model().attribute("currentKeyword", marker))
			.andExpect(content().string(containsString("の検索結果")))
			.andExpect(content().string(containsString("value=\"" + marker + "\""))); // 헤더 검색창에 검색어 유지

		List<FoundItem> items = (List<FoundItem>) mockMvc.perform(get("/items").param("q", marker).param("station", "新宿"))
			.andExpect(model().attribute("totalItems", 1))
			.andReturn().getModelAndView().getModel().get("items");
		assertThat(items).extracting(FoundItem::getId).containsExactly(shinjuku);

		mockMvc.perform(get("/lost-items").param("q", "  " + marker + "  "))
			.andExpect(model().attribute("totalItems", 1))
			.andExpect(model().attribute("currentKeyword", marker)); // 앞뒤 공백 제거
		assertThat(lostIds(mockMvc.perform(get("/lost-items").param("q", marker))
			.andReturn().getModelAndView().getModel().get("items"))).containsExactly(lost);

		// 결과가 없을 때
		mockMvc.perform(get("/items").param("q", marker + " 存在しない言葉"))
			.andExpect(model().attribute("totalItems", 0))
			.andExpect(content().string(containsString("条件に一致する投稿はありません。")));
	}

	@Test
	void 검색어가_없으면_게시판_목록은_전체를_보여준다() throws Exception {
		insertFound("傘", "新宿", marker, false);
		int all = jdbcTemplate.queryForObject("SELECT count(*) FROM FOUND_ITEM WHERE DEL_FLG = FALSE", Integer.class);

		mockMvc.perform(get("/items").param("q", " "))
			.andExpect(model().attribute("totalItems", all))
			.andExpect(model().attribute("currentKeyword", (Object) null))
			.andExpect(content().string(not(containsString("の検索結果"))));
	}

	@Test
	void 작성_일시나_날짜가_같은_글도_페이지를_넘기면_빠짐없이_한_번씩_나온다() throws Exception {
		// 같은 일시에 등록된 글 (예전 데이터를 옮겨 왔을 때 실제로 있었던 경우)
		LocalDateTime sameTime = LocalDateTime.of(2026, 1, 20, 12, 0);
		LoginMember owner = newMember();
		for (int i = 0; i < 20; i++) {
			jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, REGISTRATIONDATE)"
					+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', ?, '匿名の天使001', ?)",
				marker, sameTime);
			jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
					+ " VALUES ('[2026-01-12][山手線][渋谷][その他] 傘', '2026-01-12', '山手線', '渋谷', 'その他', '傘', ?, ?, ?, ?)",
				marker, owner.getNickname(), owner.getId(), sameTime);
		}

		for (String board : List.of("/items", "/lost-items")) {
			for (String sort : List.of("latest", "reg_asc", "date_desc", "date_asc")) {
				List<Long> ids = new ArrayList<>();
				for (int page = 1; page <= 2; page++) {
					List<?> items = (List<?>) mockMvc.perform(get(board).param("q", marker).param("sort", sort).param("page", String.valueOf(page)))
						.andReturn().getModelAndView().getModel().get("items");
					items.forEach(item -> ids.add(item instanceof FoundItem f ? f.getId() : ((LostItem) item).getId()));
				}
				assertThat(ids).as(board + " " + sort).hasSize(20).doesNotHaveDuplicates();
			}
		}
	}

	// ---------- 도우미 ----------

	private long insertFound(String itemName, String station, String contents, boolean deleted) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, DEL_FLG, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-08', '山手線', ?, 'その他', ?, ?, '匿名の天使001', ?, ?)",
			"[2026-01-08][山手線][" + station + "][その他] " + itemName, station, itemName, contents, deleted, nextTime());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long insertLost(String itemName, String station, String contents) {
		LoginMember owner = newMember();
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-12', '山手線', ?, 'その他', ?, ?, ?, ?, ?)",
			"[2026-01-12][山手線][" + (station != null ? station : "駅不明") + "][その他] " + itemName,
			station, itemName, contents, owner.getNickname(), owner.getId(), nextTime());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);
	}

	// 등록 순서대로 작성 일시가 늘어나게 해서 "최신 순" 결과를 확실하게 한다
	private LocalDateTime time = LocalDateTime.now();

	private LocalDateTime nextTime() {
		time = time.plusSeconds(1);
		return time;
	}

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("srch_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("佐藤一郎");
		form.setNickname("さがす人");
		form.setBirthDate(LocalDate.of(1995, 5, 5));
		form.setPhone("090-3333-4444");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}

	@SuppressWarnings("unchecked")
	private List<FoundItem> foundItems(Object items) {
		return (List<FoundItem>) items;
	}

	private List<Long> foundIds(Object items) {
		return foundItems(items).stream().map(FoundItem::getId).toList();
	}

	@SuppressWarnings("unchecked")
	private List<Long> lostIds(Object items) {
		return ((List<LostItem>) items).stream().map(LostItem::getId).toList();
	}
}
