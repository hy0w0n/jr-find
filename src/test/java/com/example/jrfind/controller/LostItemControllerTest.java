package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 探しています 게시판: 회원만 작성, 작성 전 주의사항 확인, 작성자 본인만 수정·삭제·찾음 표시
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LostItemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	// ---------- 로그인 필요 ----------

	@Test
	void 목록과_상세는_로그인_없이_볼_수_있다() throws Exception {
		long id = createItem(newMember());

		mockMvc.perform(get("/lost-items"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("探しています")));
		mockMvc.perform(get("/lost-items/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("見つかりました！")))); // 작성자용 버튼은 없음
	}

	@Test
	void 로그인하지_않으면_작성_수정_삭제는_로그인_화면으로_보낸다() throws Exception {
		long id = createItem(newMember());

		mockMvc.perform(get("/lost-items/new"))
			.andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(newItemRequest())
			.andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(get("/lost-items/{id}/edit", id))
			.andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(post("/lost-items/{id}/delete", id).with(csrf()))
			.andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(post("/lost-items/{id}/resolve", id).with(csrf()))
			.andExpect(redirectedUrl("/login?required"));

		assertThat(findRow(id).get("DEL_FLG")).isEqualTo(false);
		assertThat(findRow(id).get("RESOLVED")).isEqualTo(false);
	}

	// ---------- 작성 ----------

	@Test
	void 회원은_주의사항을_확인하고_닉네임으로_글을_쓴다() throws Exception {
		LoginMember member = newMember();

		mockMvc.perform(get("/lost-items/new").with(user(member)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("詐欺罪")));

		mockMvc.perform(newItemRequest("title", "<script>x</script>").with(user(member)))
			.andExpect(redirectedUrl("/lost-items"));

		Map<String, Object> row = latestRow();
		assertThat(row.get("TITLE")).isEqualTo("[2026-01-12][山手線][渋谷][財布] 黒い長財布");
		assertThat(row.get("AUTHOR")).isEqualTo(member.getNickname());
		assertThat(row.get("MEMBERID")).isEqualTo(member.getId());
		assertThat(row.get("RESOLVED")).isEqualTo(false);
	}

	@Test
	void 주의사항을_확인하지_않으면_등록되지_않는다() throws Exception {
		LoginMember member = newMember();
		int before = countRows();

		// 작성 전 주의사항의 체크 칸이 그대로 warningConfirmed 로 간다 (JS 가 꺼져 있어도 확인하고 등록할 수 있도록)
		mockMvc.perform(get("/lost-items/new").with(user(member)))
			.andExpect(content().string(containsString("<input type=\"checkbox\" id=\"warningCheck\" name=\"warningConfirmed\" value=\"true\" form=\"lostForm\">")));
		mockMvc.perform(newItemRequest("warningConfirmed", "false").with(user(member)))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage", "投稿の前に注意事項を確認してください。"));

		assertThat(countRows()).isEqualTo(before);
	}

	@Test
	void 역을_모르면_역_없이_저장하고_제목에_駅不明을_넣는다() throws Exception {
		mockMvc.perform(newItemRequest("stationName", "unknown").with(user(newMember())))
			.andExpect(redirectedUrl("/lost-items"));

		Map<String, Object> row = latestRow();
		assertThat(row.get("STATIONNAME")).isNull();
		assertThat(row.get("TITLE")).isEqualTo("[2026-01-12][山手線][駅不明][財布] 黒い長財布");

		mockMvc.perform(get("/lost-items/{id}", row.get("ID")))
			.andExpect(content().string(containsString("class=\"station-unknown\">駅不明</span>")));
	}

	@Test
	void 입력값이_잘못되면_등록하지_않고_입력값을_유지한다() throws Exception {
		LoginMember member = newMember();
		int before = countRows();

		// 노선에 없는 역
		mockMvc.perform(newItemRequest("stationName", "大阪").with(user(member)))
			.andExpect(model().attribute("errorMessage", "なくした場所(路線・駅)を正しく選択してください。"))
			.andExpect(content().string(containsString("黒い長財布"))); // 입력값 유지
		// 없는 노선 + 駅がわからない
		mockMvc.perform(newItemRequest("lineName", "存在しない線", "stationName", "unknown").with(user(member)))
			.andExpect(model().attribute("errorMessage", "なくした場所(路線・駅)を正しく選択してください。"));
		// 미래 날짜
		mockMvc.perform(newItemRequest("lostDate", LocalDate.now().plusDays(1).toString()).with(user(member)))
			.andExpect(model().attribute("errorMessage", "未来の日付は入力できません。"));
		// 필수 항목 누락
		mockMvc.perform(newItemRequest("contents", " ").with(user(member)))
			.andExpect(model().attribute("errorMessage", "詳細を入力してください。"));

		assertThat(countRows()).isEqualTo(before);
	}

	@Test
	void 본문의_줄바꿈은_화면과_같이_1글자로_센다() throws Exception {
		LoginMember member = newMember();
		// 브라우저는 줄바꿈을 CRLF(2글자)로 보낸다. 49자 + 줄바꿈을 10번 = 화면 기준 500자
		String contents = ("あ".repeat(49) + "\r\n").repeat(10);

		mockMvc.perform(newItemRequest("contents", contents).with(user(member)))
			.andExpect(redirectedUrl("/lost-items"));
		assertThat(latestRow().get("CONTENTS")).isEqualTo(contents.replace("\r\n", "\n").strip()); // 끝의 줄바꿈은 지운다
	}

	@Test
	void 너무_오래된_날짜는_등록하지_않고_여러_항목이_틀리면_항상_위의_항목부터_알려준다() throws Exception {
		LoginMember member = newMember();
		int before = countRows();

		mockMvc.perform(newItemRequest("lostDate", "1999-12-31").with(user(member)))
			.andExpect(model().attribute("errorMessage", "なくした日は2000年1月1日以降の日付を入力してください。"));
		// 날짜·노선·역·카테고리가 모두 비었을 때: 요청마다 다른 항목이 나오지 않고 맨 위의 なくした日 부터
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(newItemRequest("lostDate", "", "lineName", "", "stationName", "", "categoryName", "").with(user(member)))
				.andExpect(model().attribute("errorMessage", "なくした日を入力してください。"));
		}
		assertThat(countRows()).isEqualTo(before);
	}

	@Test
	void 아무것도_바꾸지_않고_저장하면_編集済み가_붙지_않는다() throws Exception {
		LoginMember member = newMember();
		long id = createItem(member);

		// 등록할 때와 같은 값 그대로 저장
		MockMultipartHttpServletRequestBuilder same = multipart("/lost-items/{id}/edit", id);
		same.with(csrf()).with(user(member))
			.param("lostDate", "2026-01-12").param("lineName", "山手線").param("stationName", "渋谷")
			.param("categoryName", "財布").param("itemName", "黒い長財布").param("contents", "ハチ公口の改札付近で落としたと思います。");
		mockMvc.perform(same).andExpect(redirectedUrl("/lost-items/" + id));
		assertThat(findRow(id).get("MODIFIEDDATE")).isNull();

		mockMvc.perform(editRequest(id, "黒い長財布").with(user(member))).andExpect(redirectedUrl("/lost-items/" + id));
		assertThat(findRow(id).get("MODIFIEDDATE")).isNotNull();
	}

	@Test
	void 역_필터의_駅不明으로_역을_모르고_쓴_글만_볼_수_있다() throws Exception {
		LoginMember member = newMember();
		String marker = "駅不明" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(newItemRequest("itemName", marker, "stationName", "unknown").with(user(member)));
		mockMvc.perform(newItemRequest("itemName", marker).with(user(member)));

		mockMvc.perform(get("/lost-items").param("q", marker).param("station", "unknown"))
			.andExpect(model().attribute("totalItems", 1));
		mockMvc.perform(get("/lost-items").param("q", marker))
			.andExpect(model().attribute("totalItems", 2));
	}

	@Test
	void 필터로_거른_결과가_없으면_条件に一致する_안내를_보여준다() throws Exception {
		mockMvc.perform(get("/lost-items").param("category", "存在しないカテゴリ"))
			.andExpect(content().string(containsString("条件に一致する投稿はありません。")))
			.andExpect(content().string(not(containsString("登録されている投稿はありません。"))));
		mockMvc.perform(get("/items").param("category", "存在しないカテゴリ"))
			.andExpect(content().string(containsString("条件に一致する投稿はありません。")));
	}

	@Test
	void 상세_화면에는_シェア_버튼이_있고_삭제_확인에는_댓글_안내가_있다() throws Exception {
		LoginMember member = newMember();
		long id = createItem(member);

		String share = "<button type=\"button\" class=\"btn-action js-only\" id=\"shareButton\">";
		mockMvc.perform(get("/lost-items/{id}", id).with(user(member)))
			.andExpect(content().string(containsString(share)))
			.andExpect(content().string(containsString("この投稿に付いたコメントも表示されなくなります。")))
			.andExpect(content().string(containsString("<html lang=\"ja\"")));
		mockMvc.perform(get("/lost-items/{id}", id))
			.andExpect(content().string(containsString(share))); // 로그인하지 않아도
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', '匿名の天使001', CURRENT_TIMESTAMP)");
		mockMvc.perform(get("/items/{id}", jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class)))
			.andExpect(content().string(containsString(share)))
			.andExpect(content().string(not(containsString("(予定)"))));
	}

	// ---------- 수정·삭제·찾음 표시 ----------

	@Test
	void 작성자_본인만_수정_삭제_찾음_표시를_할_수_있다() throws Exception {
		LoginMember owner = newMember();
		LoginMember other = newMember();
		long id = createItem(owner);

		// 상세 화면: 본인에게만 버튼 표시
		mockMvc.perform(get("/lost-items/{id}", id).with(user(owner)))
			.andExpect(content().string(containsString("見つかりました！")));
		mockMvc.perform(get("/lost-items/{id}", id).with(user(other)))
			.andExpect(content().string(not(containsString("見つかりました！"))));

		// 다른 회원: 수정 화면은 상세로 돌려보내고, 요청은 403
		mockMvc.perform(get("/lost-items/{id}/edit", id).with(user(other)))
			.andExpect(redirectedUrl("/lost-items/" + id));
		mockMvc.perform(editRequest(id, "書き換え").with(user(other)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/lost-items/{id}/resolve", id).with(user(other)).with(csrf()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/lost-items/{id}/delete", id).with(user(other)).with(csrf()))
			.andExpect(status().isForbidden());
		assertThat(findRow(id).get("ITEMNAME")).isEqualTo("黒い長財布");

		// 본인: 수정 → 찾음 표시 → 해제 → 삭제
		mockMvc.perform(get("/lost-items/{id}/edit", id).with(user(owner)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("黒い長財布")));
		mockMvc.perform(editRequest(id, "茶色い財布").with(user(owner)))
			.andExpect(redirectedUrl("/lost-items/" + id));
		assertThat(findRow(id).get("TITLE")).isEqualTo("[2026-01-12][山手線][駅不明][財布] 茶色い財布");
		assertThat(findRow(id).get("MODIFIEDDATE")).isNotNull();

		mockMvc.perform(post("/lost-items/{id}/resolve", id).with(user(owner)).with(csrf()).param("resolved", "true"))
			.andExpect(redirectedUrl("/lost-items/" + id));
		assertThat(findRow(id).get("RESOLVED")).isEqualTo(true);
		mockMvc.perform(get("/lost-items/{id}", id))
			.andExpect(content().string(containsString("見つかりました")));

		mockMvc.perform(post("/lost-items/{id}/resolve", id).with(user(owner)).with(csrf()).param("resolved", "false"));
		assertThat(findRow(id).get("RESOLVED")).isEqualTo(false);

		mockMvc.perform(post("/lost-items/{id}/delete", id).with(user(owner)).with(csrf()))
			.andExpect(redirectedUrl("/lost-items"));
		mockMvc.perform(get("/lost-items/{id}", id))
			.andExpect(status().isNotFound());
	}

	// ---------- 목록 ----------

	@Test
	@SuppressWarnings("unchecked")
	void 목록은_노선_역_카테고리로_거를_수_있다() throws Exception {
		LoginMember member = newMember();
		String marker = UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(newItemRequest("itemName", "リュック " + marker, "categoryName", "カバン・バッグ", "stationName", "恵比寿").with(user(member)));

		List<LostItem> items = (List<LostItem>) mockMvc.perform(get("/lost-items")
				.param("line", "山手線").param("station", "恵比寿").param("category", "カバン・バッグ"))
			.andExpect(status().isOk())
			.andReturn().getModelAndView().getModel().get("items");

		assertThat(items).isNotEmpty();
		assertThat(items).allMatch(item -> "恵比寿".equals(item.getStationName()) && "カバン・バッグ".equals(item.getCategoryName()));
		assertThat(items).anyMatch(item -> item.getItemName().equals("リュック " + marker));
	}

	// ---------- 도우미 ----------

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("lost_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("さがす人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}

	// 정상 입력값으로 채운 등록 요청. overrides 는 "항목명", "값" 순서로 넘기면 해당 값만 바꾼다.
	private MockMultipartHttpServletRequestBuilder newItemRequest(String... overrides) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("lostDate", "2026-01-12");
		params.put("lineName", "山手線");
		params.put("stationName", "渋谷");
		params.put("categoryName", "財布");
		params.put("itemName", "黒い長財布");
		params.put("contents", "ハチ公口の改札付近で落としたと思います。");
		params.put("warningConfirmed", "true");
		for (int i = 0; i < overrides.length; i += 2) {
			params.put(overrides[i], overrides[i + 1]);
		}

		MockMultipartHttpServletRequestBuilder request = multipart("/lost-items/new");
		request.with(csrf());
		params.forEach((name, value) -> request.param(name, value));
		return request;
	}

	private MockMultipartHttpServletRequestBuilder editRequest(long id, String itemName) {
		MockMultipartHttpServletRequestBuilder request = multipart("/lost-items/{id}/edit", id);
		request.with(csrf());
		request.param("lostDate", "2026-01-12")
			.param("lineName", "山手線")
			.param("stationName", "unknown")
			.param("categoryName", "財布")
			.param("itemName", itemName)
			.param("contents", "内容を修正しました。");
		return request;
	}

	private long createItem(LoginMember member) throws Exception {
		mockMvc.perform(newItemRequest().with(user(member))).andExpect(redirectedUrl("/lost-items"));
		return (Long) latestRow().get("ID");
	}

	private Map<String, Object> latestRow() {
		return jdbcTemplate.queryForMap("SELECT * FROM LOST_ITEM ORDER BY ID DESC LIMIT 1");
	}

	private Map<String, Object> findRow(long id) {
		return jdbcTemplate.queryForMap("SELECT * FROM LOST_ITEM WHERE ID = ?", id);
	}

	private int countRows() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM LOST_ITEM", Integer.class);
	}
}
