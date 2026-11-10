package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.repository.JrfindRepository;
import com.example.jrfind.repository.KeywordCondition;
import com.example.jrfind.repository.LostItemRepository;
import com.example.jrfind.repository.SearchTextBackfill;
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

	// SQL 로 직접 넣은 글은 검색용 컬럼이 비어 있으므로 채운다 (예전 DB 의 글을 서버를 켤 때 채우는 것과 같은 처리)
	@Autowired
	private SearchTextBackfill searchTextBackfill;

	@Autowired
	private JrfindRepository foundRepository;

	@Autowired
	private LostItemRepository lostRepository;

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
			.andExpect(content().string(containsString("該当する投稿はありません。"))) // 探しています 쪽
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
	void 전각_반각을_구분하지_않는다() throws Exception {
		long iphone = insertFound("iPhone 15", "新宿", marker, false);
		long suica = insertFound("ＳＵＩＣＡカード", "新宿", marker + " （予定）", false);
		long smartphone = insertFound("スマホ", "新宿", marker, false);
		long gum = insertFound("ｶﾞﾑ ｲﾔﾎﾝ", "新宿", marker, false);

		// 검색어가 전각 영숫자 / 반각 카나 (일본어 입력기에서 흔히 그대로 입력된다)
		assertThat(foundIds(searchFound(toFullWidth(marker) + " ｉＰｈｏｎｅ"))).containsExactly(iphone);
		assertThat(foundIds(searchFound(marker + " ｽﾏﾎ"))).containsExactly(smartphone);
		// 글이 전각 영숫자 / 반각 카나(탁점 포함), 검색어는 반각 / 전각
		assertThat(foundIds(searchFound(marker + " suica"))).containsExactly(suica);
		assertThat(foundIds(searchFound(marker + " ガム イヤホン"))).containsExactly(gum);
		// 전각 기호가 들어 있는 글은 전각·반각 어느 기호로도 찾는다
		assertThat(foundIds(searchFound(marker + " （予定）"))).containsExactly(suica);
		assertThat(foundIds(searchFound(marker + " (予定)"))).containsExactly(suica);
		// 맞추면 같은 단어가 되는 검색어 (ｽﾏﾎ スマホ)
		assertThat(foundIds(searchFound(marker + " ｽﾏﾎ スマホ"))).containsExactly(smartphone);
	}

	@Test
	void 검색용_값은_제목과_본문을_줄바꿈으로_이어서_맞춘다() {
		assertThat(KeywordCondition.searchText("[2026-01-08][山手線][新宿][その他] ｽﾏﾎ", "Ｓｕｉｃａ（予定）"))
			.isEqualTo("[2026-01-08][山手線][新宿][その他] スマホ\nsuica(予定)");
	}

	@Test
	void 저장하거나_수정한_글은_바로_새_내용으로_찾는다() throws Exception {
		FoundItem found = new FoundItem();
		found.setTitle("[2026-01-08][山手線][新宿][その他] ｽﾏﾎ");
		found.setFoundDate(LocalDate.of(2026, 1, 8));
		found.setLineName("山手線");
		found.setStationName("新宿");
		found.setCategoryName("その他");
		found.setItemName("ｽﾏﾎ");
		found.setContents(marker + " 古い本文");
		found.setAuthor("匿名の天使001");
		found.setRegistrationDate(nextTime());
		foundRepository.save(found);
		long foundId = jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);

		LoginMember owner = newMember();
		LostItem lost = new LostItem();
		lost.setTitle("[2026-01-12][山手線][駅不明][その他] Ｓｕｉｃａ");
		lost.setLostDate(LocalDate.of(2026, 1, 12));
		lost.setLineName("山手線");
		lost.setCategoryName("その他");
		lost.setItemName("Ｓｕｉｃａ");
		lost.setContents(marker + " 古い本文");
		lost.setAuthor(owner.getNickname());
		lost.setMemberId(owner.getId());
		lost.setRegistrationDate(nextTime());
		lostRepository.save(lost);
		long lostId = jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);

		assertThat(foundIds(searchFound(marker + " スマホ 古い"))).containsExactly(foundId);
		assertThat(lostIds(searchLost(marker + " suica 古い"))).containsExactly(lostId);

		found = foundRepository.findById(foundId);
		found.setContents(marker + " 新しい本文");
		found.setModifiedDate(nextTime());
		assertThat(foundRepository.update(found, null)).isTrue();
		lost = lostRepository.findById(lostId);
		lost.setContents(marker + " 新しい本文");
		lost.setModifiedDate(nextTime());
		assertThat(lostRepository.update(lost, null)).isTrue();

		assertThat(foundIds(searchFound(marker + " 古い"))).isEmpty();
		assertThat(foundIds(searchFound(marker + " スマホ 新しい"))).containsExactly(foundId);
		assertThat(lostIds(searchLost(marker + " 古い"))).isEmpty();
		assertThat(lostIds(searchLost(marker + " suica 新しい"))).containsExactly(lostId);
	}

	@Test
	void 검색어가_5단어를_넘으면_앞의_5단어로_찾고_안내한다() throws Exception {
		insertFound("黒い財布", "新宿", marker + " 南口の改札にありました", false);
		String sixWords = marker + " 黒い 財布 南口 改札 存在しない言葉";

		mockMvc.perform(get("/search").param("q", sixWords))
			.andExpect(model().attribute("keywordTruncated", true))
			.andExpect(model().attribute("foundCount", 1))
			.andExpect(content().string(containsString("キーワードは5語までです。最初の5語で検索しています。")));
		mockMvc.perform(get("/items").param("q", sixWords))
			.andExpect(model().attribute("keywordTruncated", true))
			.andExpect(model().attribute("totalItems", 1))
			.andExpect(content().string(containsString("キーワードは5語までです。")));

		mockMvc.perform(get("/search").param("q", marker + " 黒い 財布 南口 改札"))
			.andExpect(model().attribute("keywordTruncated", false))
			.andExpect(content().string(not(containsString("キーワードは5語までです。"))));
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
			.andExpect(content().string(containsString("value=\"" + marker + "\""))); // 게시판 안 검색창에 검색어 유지

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
	void 게시판_안_검색창은_그_게시판으로_보내고_지금_고른_필터와_정렬을_함께_보낸다() throws Exception {
		insertFound("傘", "新宿", marker, false);
		insertFound("傘", "渋谷", marker, false);
		insertLost("傘", "新宿", marker);

		String boardForm = form(page(get("/items").param("q", marker).param("station", "新宿").param("sort", "date_asc")), "id=\"boardSearch\"");
		assertThat(boardForm).contains("action=\"/items\"", "name=\"q\"", "value=\"" + marker + "\"",
			"name=\"station\" value=\"新宿\"", "name=\"sort\" value=\"date_asc\"");
		assertThat(form(page(get("/lost-items").param("category", "その他")), "id=\"boardSearch\""))
			.contains("action=\"/lost-items\"", "name=\"category\" value=\"その他\"");

		// 검색창이 보내는 그대로 (고르지 않은 필터는 빈 값). 다른 게시판의 글은 나오지 않는다
		mockMvc.perform(get("/items").param("line", "").param("station", "新宿").param("category", "").param("sort", "latest").param("q", marker))
			.andExpect(model().attribute("totalItems", 1));
		mockMvc.perform(get("/lost-items").param("line", "").param("station", "").param("category", "").param("sort", "latest").param("q", marker))
			.andExpect(model().attribute("totalItems", 1));
	}

	@Test
	void 헤더_검색창은_통합_검색_화면에서만_검색어를_채운다() throws Exception {
		assertThat(form(page(get("/search").param("q", marker)), "class=\"header-search\""))
			.contains("action=\"/search\"", "value=\"" + marker + "\"");
		// 게시판 목록에서는 게시판 안 검색창에만 표시
		assertThat(form(page(get("/items").param("q", marker)), "class=\"header-search\"")).doesNotContain(marker);
		assertThat(form(page(get("/lost-items").param("q", marker)), "class=\"header-search\"")).doesNotContain(marker);
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
		searchTextBackfill.fillMissing();

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

	// 통합 검색의 見つけた 결과 (최신 5건)
	private Object searchFound(String q) throws Exception {
		return mockMvc.perform(get("/search").param("q", q)).andReturn().getModelAndView().getModel().get("foundItems");
	}

	// 통합 검색의 探しています 결과 (최신 5건)
	private Object searchLost(String q) throws Exception {
		return mockMvc.perform(get("/search").param("q", q)).andReturn().getModelAndView().getModel().get("lostItems");
	}

	// 반각 영숫자 → 전각 (A → Ａ)
	private static String toFullWidth(String text) {
		StringBuilder result = new StringBuilder();
		text.chars().forEach(c -> result.append((char) (c + 0xFEE0)));
		return result.toString();
	}

	private String page(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// html 에서 attribute 가 붙은 <form> ~ </form> 부분
	private String form(String html, String attribute) {
		int start = html.lastIndexOf("<form", html.indexOf(attribute));
		return html.substring(start, html.indexOf("</form>", start));
	}

	private long insertFound(String itemName, String station, String contents, boolean deleted) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, DEL_FLG, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-08', '山手線', ?, 'その他', ?, ?, '匿名の天使001', ?, ?)",
			"[2026-01-08][山手線][" + station + "][その他] " + itemName, station, itemName, contents, deleted, nextTime());
		searchTextBackfill.fillMissing();
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long insertLost(String itemName, String station, String contents) {
		LoginMember owner = newMember();
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-12', '山手線', ?, 'その他', ?, ?, ?, ?, ?)",
			"[2026-01-12][山手線][" + (station != null ? station : "駅不明") + "][その他] " + itemName,
			station, itemName, contents, owner.getNickname(), owner.getId(), nextTime());
		searchTextBackfill.fillMissing();
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
