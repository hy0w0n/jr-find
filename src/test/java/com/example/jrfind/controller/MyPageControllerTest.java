package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.jrfind.domain.Comment;
import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 마이페이지: 로그인 필요, 내가 쓴 글·댓글, 회원정보 변경(작성자명·세션 반영), 비밀번호 변경
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPageControllerTest {
	private static final String PASSWORD = "secret123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	// ---------- 로그인 필요 ----------

	@Test
	void 로그인하지_않으면_마이페이지는_로그인_화면으로_보낸다() throws Exception {
		mockMvc.perform(get("/mypage")).andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(get("/mypage/edit")).andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(get("/mypage/password")).andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(post("/mypage/edit").with(csrf()).param("realName", "x"))
			.andExpect(redirectedUrl("/login?required"));
	}

	@Test
	void 헤더의_유저_아이콘과_닉네임은_마이페이지로_연결된다() throws Exception {
		mockMvc.perform(get("/items").with(user(loginMember(signup()))))
			.andExpect(content().string(containsString("href=\"/mypage\" class=\"auth-icon\"")))
			.andExpect(content().string(containsString("href=\"/mypage\" class=\"auth-name\"")));
	}

	// ---------- 내가 쓴 글·댓글 ----------

	@Test
	@SuppressWarnings("unchecked")
	void 마이페이지에는_내가_쓴_글과_댓글만_보인다() throws Exception {
		LoginMember me = loginMember(signup());
		LoginMember other = loginMember(signup());

		long myFound = insertFound(me, "私の傘", false);
		insertFound(me, "削除した傘", true);
		long othersFound = insertFound(other, "他人の傘", false);
		insertFound(null, "ログインせずに投稿", false);
		long myLost = insertLost(me, "私の財布", false);
		insertLost(other, "他人の財布", false);

		comment(me, "/items/" + othersFound, "他人の投稿へのコメント");
		comment(me, "/lost-items/" + myLost, "自分の投稿へのコメント");
		comment(other, "/lost-items/" + myLost, "他人のコメント");
		long deletedPost = insertFound(other, "あとで削除される投稿", false);
		comment(me, "/items/" + deletedPost, "削除された投稿へのコメント");
		jdbcTemplate.update("UPDATE FOUND_ITEM SET DEL_FLG = TRUE WHERE ID = ?", deletedPost);
		comment(me, "/items/" + othersFound, "削除したコメント");
		jdbcTemplate.update("UPDATE POST_COMMENT SET DEL_FLG = TRUE WHERE CONTENTS = '削除したコメント' AND MEMBERID = ?", me.getId());

		// 기본 탭: 見つけた
		Map<String, Object> model = mockMvc.perform(get("/mypage").with(user(me)))
			.andExpect(status().isOk())
			.andExpect(model().attribute("tab", "found"))
			.andExpect(model().attribute("foundCount", 1))
			.andExpect(model().attribute("lostCount", 1))
			.andExpect(model().attribute("commentCount", 2))
			.andExpect(content().string(containsString("090-1234-5678"))) // 본인 정보
			.andReturn().getModelAndView().getModel();
		assertThat((List<FoundItem>) model.get("foundItems")).extracting(FoundItem::getId).containsExactly(myFound);

		model = mockMvc.perform(get("/mypage").param("tab", "lost").with(user(me)))
			.andReturn().getModelAndView().getModel();
		assertThat((List<LostItem>) model.get("lostItems")).extracting(LostItem::getId).containsExactly(myLost);

		model = mockMvc.perform(get("/mypage").param("tab", "comments").with(user(me)))
			.andExpect(content().string(containsString("data-href=\"/items/" + othersFound + "#comment-")))
			.andReturn().getModelAndView().getModel();
		List<Comment> comments = (List<Comment>) model.get("comments");
		assertThat(comments).extracting(Comment::getContents)
			.containsExactly("自分の投稿へのコメント", "他人の投稿へのコメント"); // 최신 순
		assertThat(comments.get(0).getPostTitle()).contains("私の財布");
		assertThat(comments.get(1).getPostUrl()).startsWith("/items/" + othersFound + "#comment-");

		// 정해진 값이 아닌 탭은 見つけた
		mockMvc.perform(get("/mypage").param("tab", "<script>").with(user(me)))
			.andExpect(model().attribute("tab", "found"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 탭_목록은_10건씩_나뉜다() throws Exception {
		LoginMember me = loginMember(signup());
		for (int i = 0; i < 12; i++) {
			insertFound(me, "傘" + i, false);
		}

		mockMvc.perform(get("/mypage").with(user(me)))
			.andExpect(model().attribute("totalPages", 2));
		List<FoundItem> page2 = (List<FoundItem>) mockMvc.perform(get("/mypage").param("page", "2").with(user(me)))
			.andReturn().getModelAndView().getModel().get("foundItems");
		assertThat(page2).extracting(FoundItem::getItemName).containsExactly("傘1", "傘0");

		// 범위를 벗어난 페이지 번호
		mockMvc.perform(get("/mypage").param("page", "99").with(user(me)))
			.andExpect(model().attribute("currentPage", 2));
		mockMvc.perform(get("/mypage").param("tab", "comments").param("page", "-1").with(user(me)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("コメントはまだありません。")));
	}

	// ---------- 会員情報変更 ----------

	@Test
	void 회원정보를_바꾸면_지금까지_쓴_글과_댓글의_작성자명과_헤더도_바뀐다() throws Exception {
		String loginId = signup();
		LoginMember me = loginMember(loginId);
		LoginMember other = loginMember(signup());
		long found = insertFound(me, "傘", false);
		long lost = insertLost(me, "財布", false);
		long othersFound = insertFound(other, "他人の傘", false);
		comment(me, "/items/" + othersFound, "コメント");
		MockHttpSession session = login(loginId);

		mockMvc.perform(get("/mypage/edit").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"さがす人\""))); // 지금 값으로 채움

		mockMvc.perform(profileRequest("鈴木次郎", "新しい名前", "1990-12-24", "080-9999-0000").session(session))
			.andExpect(redirectedUrl("/mypage?updated"));

		Map<String, Object> row = memberRow(me.getId());
		assertThat(row.get("REALNAME")).isEqualTo("鈴木次郎");
		assertThat(row.get("NICKNAME")).isEqualTo("新しい名前");
		assertThat(row.get("BIRTHDATE").toString()).isEqualTo("1990-12-24");
		assertThat(row.get("PHONE")).isEqualTo("080-9999-0000");
		assertThat(row.get("LOGINID")).isEqualTo(loginId); // ID 는 그대로

		assertThat(author("FOUND_ITEM", found)).isEqualTo("新しい名前");
		assertThat(author("LOST_ITEM", lost)).isEqualTo("新しい名前");
		assertThat(jdbcTemplate.queryForObject("SELECT AUTHOR FROM POST_COMMENT WHERE MEMBERID = ?", String.class, me.getId()))
			.isEqualTo("新しい名前");
		assertThat(author("FOUND_ITEM", othersFound)).isEqualTo(other.getNickname()); // 다른 회원 글은 그대로

		// 다시 로그인하지 않아도 헤더 닉네임이 바뀐다
		mockMvc.perform(get("/mypage").param("updated", "").session(session))
			.andExpect(content().string(containsString("新しい名前 さん")))
			.andExpect(content().string(containsString("会員情報を変更しました。")));

		// 닉네임을 비우면 氏名
		mockMvc.perform(profileRequest("鈴木次郎", "", "1990-12-24", "080-9999-0000").session(session));
		assertThat(memberRow(me.getId()).get("NICKNAME")).isEqualTo("鈴木次郎");
		assertThat(author("FOUND_ITEM", found)).isEqualTo("鈴木次郎");
	}

	@Test
	void 회원정보_입력값이_잘못되면_저장하지_않고_입력값을_유지한다() throws Exception {
		LoginMember me = loginMember(signup());

		mockMvc.perform(profileRequest(" ", "新しい名前", "2999-01-01", "abc").with(user(me)))
			.andExpect(status().isOk())
			.andExpect(view().name("profileEdit"))
			.andExpect(model().attributeHasFieldErrors("profileForm", "realName", "birthDate", "phone"))
			.andExpect(content().string(containsString("value=\"新しい名前\"")));

		mockMvc.perform(profileRequest("鈴木次郎", "匿名の天使 001", "1990-12-24", "080-9999-0000").with(user(me)))
			.andExpect(model().attributeHasFieldErrorCode("profileForm", "nickname", "reserved"));

		assertThat(memberRow(me.getId()).get("NICKNAME")).isEqualTo("さがす人");
		assertThat(memberRow(me.getId()).get("REALNAME")).isEqualTo("山田太郎");
	}

	// ---------- パスワード変更 ----------

	@Test
	void 비밀번호를_바꾸면_새_비밀번호로만_로그인할_수_있다() throws Exception {
		String loginId = signup();
		MockHttpSession session = login(loginId);

		// 현재 비밀번호 틀림 / 확인 불일치 / 같은 비밀번호 / 규칙 위반
		mockMvc.perform(passwordRequest("wrong1234", "newpass456", "newpass456").session(session))
			.andExpect(model().attributeHasFieldErrorCode("passwordForm", "currentPassword", "mismatch"));
		mockMvc.perform(passwordRequest(PASSWORD, "newpass456", "newpass789").session(session))
			.andExpect(model().attributeHasFieldErrorCode("passwordForm", "newPasswordConfirm", "mismatch"));
		mockMvc.perform(passwordRequest(PASSWORD, PASSWORD, PASSWORD).session(session))
			.andExpect(model().attributeHasFieldErrorCode("passwordForm", "newPassword", "same"));
		mockMvc.perform(passwordRequest(PASSWORD, "short", "short").session(session))
			.andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"))
			.andExpect(content().string(not(containsString("value=\"short\"")))); // 비밀번호는 다시 채우지 않음

		mockMvc.perform(passwordRequest(PASSWORD, "newpass456", "newpass456").session(session))
			.andExpect(redirectedUrl("/mypage?passwordChanged"));

		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(PASSWORD))
			.andExpect(redirectedUrl("/login?error"));
		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password("newpass456"))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void 현재_비밀번호를_연속으로_틀리면_잠긴다() throws Exception {
		LoginMember me = loginMember(signup());
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(passwordRequest("wrong1234", "newpass456", "newpass456").with(user(me)))
				.andExpect(model().attributeHasFieldErrorCode("passwordForm", "currentPassword", "mismatch"));
		}

		// 맞는 비밀번호여도 잠긴 동안은 바꿀 수 없다
		mockMvc.perform(passwordRequest(PASSWORD, "newpass456", "newpass456").with(user(me)))
			.andExpect(model().attributeHasFieldErrorCode("passwordForm", "currentPassword", "locked"));
		assertThat(memberService.matchesPassword(me.getId(), PASSWORD)).isTrue();
	}

	// ---------- 도우미 ----------

	private String signup() {
		SignupForm form = new SignupForm();
		form.setLoginId("my_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword(PASSWORD);
		form.setPasswordConfirm(PASSWORD);
		form.setRealName("山田太郎");
		form.setNickname("さがす人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return form.getLoginId();
	}

	private LoginMember loginMember(String loginId) {
		return (LoginMember) memberService.loadUserByUsername(loginId);
	}

	// 실제 로그인 화면으로 로그인한 세션 (세션에 저장된 로그인 정보가 바뀌는지 확인용)
	private MockHttpSession login(String loginId) throws Exception {
		return (MockHttpSession) mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(PASSWORD))
			.andExpect(redirectedUrl("/"))
			.andReturn().getRequest().getSession(false);
	}

	private MockHttpServletRequestBuilder profileRequest(String realName, String nickname, String birthDate, String phone) {
		return post("/mypage/edit").with(csrf())
			.param("realName", realName)
			.param("nickname", nickname)
			.param("birthDate", birthDate)
			.param("phone", phone);
	}

	private MockHttpServletRequestBuilder passwordRequest(String current, String newPassword, String confirm) {
		return post("/mypage/password").with(csrf())
			.param("currentPassword", current)
			.param("newPassword", newPassword)
			.param("newPasswordConfirm", confirm);
	}

	private void comment(LoginMember member, String postUrl, String contents) throws Exception {
		mockMvc.perform(post(postUrl + "/comments").with(user(member)).with(csrf()).param("contents", contents))
			.andExpect(status().is3xxRedirection());
	}

	// 등록 순서대로 작성 일시가 늘어나게 해서 "최신 순" 결과를 확실하게 한다
	private LocalDateTime time = LocalDateTime.now();

	private LocalDateTime nextTime() {
		time = time.plusSeconds(1);
		return time;
	}

	// member 가 null 이면 비회원 글
	private long insertFound(LoginMember member, String itemName, boolean deleted) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, DEL_FLG, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-08', '山手線', '新宿', 'その他', ?, '内容', ?, ?, ?, ?)",
			"[2026-01-08][山手線][新宿][その他] " + itemName, itemName,
			member != null ? member.getNickname() : "匿名の天使 001", member != null ? member.getId() : null, deleted, nextTime());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long insertLost(LoginMember member, String itemName, boolean deleted) {
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, DEL_FLG, REGISTRATIONDATE)"
				+ " VALUES (?, '2026-01-12', '山手線', '渋谷', 'その他', ?, '内容', ?, ?, ?, ?)",
			"[2026-01-12][山手線][渋谷][その他] " + itemName, itemName, member.getNickname(), member.getId(), deleted, nextTime());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);
	}

	private Map<String, Object> memberRow(long id) {
		return jdbcTemplate.queryForMap("SELECT * FROM MEMBER WHERE ID = ?", id);
	}

	private String author(String table, long id) {
		return jdbcTemplate.queryForObject("SELECT AUTHOR FROM " + table + " WHERE ID = ?", String.class, id);
	}
}
