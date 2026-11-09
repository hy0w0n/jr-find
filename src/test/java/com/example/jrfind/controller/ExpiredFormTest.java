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
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 화면을 오래 열어 둬서 폼을 보낼 수 없게 된 경우 (CSRF 토큰 불일치): 403 대신 보던 화면으로 돌려보내고 안내한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpiredFormTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	@Test
	void 로그인한_채로_보낼_수_없게_된_댓글은_보던_화면의_입력_칸에_다시_채운다() throws Exception {
		LoginMember member = newMember();
		long postId = insertFound();
		int before = countComments();

		MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/items/{id}/comments", postId)
				.with(user(member)).with(csrf().useInvalidToken())
				.param("contents", "届けてくださりありがとうございます。")
				.header("Referer", "http://localhost/items/" + postId))
			.andExpect(redirectedUrl("/items/" + postId + "?expired#commentForm"))
			.andReturn().getRequest().getSession();
		assertThat(countComments()).isEqualTo(before);

		// 돌아간 화면: 안내 문구와, 입력 칸에 쓰던 댓글
		mockMvc.perform(get("/items/{id}", postId).param("expired", "").session(session).with(user(member)))
			.andExpect(content().string(containsString("時間が経過したため、送信できませんでした。")))
			.andExpect(content().string(containsString(">届けてくださりありがとうございます。</textarea>")));
	}

	@Test
	void 로그인이_끝난_뒤에_보낸_댓글은_다시_로그인하고_돌아오면_입력_칸에_채운다() throws Exception {
		LoginMember member = newMember();
		long postId = insertLost(member);

		MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/lost-items/{id}/comments", postId)
				.with(csrf().useInvalidToken())
				.param("contents", "下書きのコメント")
				.header("Referer", "http://localhost/lost-items/" + postId))
			.andExpect(redirectedUrl("/lost-items/" + postId + "?expired#commentForm"))
			.andReturn().getRequest().getSession();

		mockMvc.perform(get("/lost-items/{id}", postId).param("expired", "").session(session))
			.andExpect(content().string(containsString("ログインが必要な操作は、もう一度ログインしてからお試しください。")));

		// 로그인하기 전의 상세 화면에서는 꺼내지 않는다
		mockMvc.perform(get("/lost-items/{id}", postId).session(session))
			.andExpect(content().string(not(containsString("下書きのコメント"))));

		// 댓글 영역의 「ログイン」 → 로그인 → 돌아오면 입력 칸에 다시 채운다 (한 번만)
		mockMvc.perform(get("/lost-items/{id}/comments", postId).session(session).with(user(member)))
			.andExpect(redirectedUrl("/lost-items/" + postId + "#commentForm"));
		mockMvc.perform(get("/lost-items/{id}", postId).session(session).with(user(member)))
			.andExpect(content().string(containsString(">下書きのコメント</textarea>")));
		mockMvc.perform(get("/lost-items/{id}/comments", postId).session(session).with(user(member)))
			.andExpect(redirectedUrl("/lost-items/" + postId + "#comments"));
	}

	@Test
	void 보내지_못한_입력은_한_세션에_하나만_정해진_크기까지만_보관한다() throws Exception {
		long postId = insertFound();
		// 로그인하지 않은 채로 여러 글에 큰 댓글을 보내도 마지막 하나만 남는다 (서버 메모리가 늘지 않도록)
		MockHttpSession session = new MockHttpSession();
		for (long id = postId; id < postId + 5; id++) {
			mockMvc.perform(post("/items/{id}/comments", id).with(csrf().useInvalidToken()).session(session).param("contents", "下書き" + id));
		}
		assertThat(Collections.list(session.getAttributeNames())).filteredOn(name -> name.contains("FormDraft")).hasSize(1);
		assertThat(session.getAttribute(FormDraft.class.getName())).extracting("uri").isEqualTo("/items/" + (postId + 4) + "/comments");

		// 너무 큰 입력은 보관하지 않는다 (댓글은 500자까지라 정상적인 입력은 들어간다)
		MockHttpSession other = new MockHttpSession();
		mockMvc.perform(post("/items/{id}/comments", postId).with(csrf().useInvalidToken()).session(other)
				.param("contents", "あ".repeat(FormDraft.MAX_CHARS + 1)));
		assertThat(other.getAttribute(FormDraft.class.getName())).isNull();
	}

	@Test
	void 시간이_지나_보내지_못한_등록_수정은_같은_화면을_다시_열면_입력한_내용을_채운다() throws Exception {
		// 등록 (見つけた, 비회원): 비밀번호 칸은 보관하지 않는다
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(multipart("/items/new").with(csrf().useInvalidToken()).session(session)
				.param("foundDate", "2026-01-10").param("lineName", "山手線").param("stationName", "新宿")
				.param("categoryName", "財布").param("itemName", "期限切れの財布").param("contents", "本文").param("password", "1234")
				.header("Referer", "http://localhost/items/new"))
			.andExpect(redirectedUrl("/items/new?expired"));
		assertThat(((FormDraft) session.getAttribute(FormDraft.class.getName())).fields()).doesNotContainKey("password");
		mockMvc.perform(get("/items/new").param("expired", "").session(session))
			.andExpect(content().string(containsString("value=\"期限切れの財布\"")))
			.andExpect(model().attribute("errorMessage", "入力していた内容を元に戻しました。内容を確認して、もう一度送信してください。"));

		// 수정 (見つけた, 비회원 글): 세션이 끝나 비밀번호 확인도 풀렸으면 → 비밀번호를 다시 묻고, 확인하면 수정 화면에 채운다
		long postId = insertFound();
		MockHttpSession expired = new MockHttpSession();
		mockMvc.perform(multipart("/items/{id}/edit", postId).with(csrf().useInvalidToken()).session(expired)
				.param("foundDate", "2026-01-08").param("lineName", "山手線").param("stationName", "新宿")
				.param("categoryName", "その他").param("itemName", "直した傘").param("contents", "直した本文")
				.header("Referer", "http://localhost/items/" + postId + "/edit"))
			.andExpect(redirectedUrl("/items/" + postId + "/edit?expired"));
		mockMvc.perform(get("/items/{id}/edit", postId).param("expired", "").session(expired))
			.andExpect(redirectedUrl("/items/" + postId + "?reverify&expired"));

		// 수정 (探しています, 회원 글): 로그인이 끝났으면 다시 로그인한 뒤 채운다
		LoginMember member = newMember();
		long lostId = insertLost(member);
		MockHttpSession lost = new MockHttpSession();
		mockMvc.perform(multipart("/lost-items/{id}/edit", lostId).with(csrf().useInvalidToken()).session(lost)
				.param("lostDate", "2026-01-12").param("lineName", "山手線").param("stationName", "渋谷")
				.param("categoryName", "その他").param("itemName", "青い傘").param("contents", "直した本文")
				.header("Referer", "http://localhost/lost-items/" + lostId + "/edit"))
			.andExpect(redirectedUrl("/lost-items/" + lostId + "/edit?expired"));
		mockMvc.perform(get("/lost-items/{id}/edit", lostId).param("expired", "").session(lost).with(user(member)))
			.andExpect(content().string(containsString("value=\"青い傘\"")));
	}

	@Test
	void 로그인이_끝난_채로_회원_글_수정_화면을_열면_로그인한_뒤_돌아온다() throws Exception {
		LoginMember member = newMember();
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', '改札で拾いました。', ?, ?, CURRENT_TIMESTAMP)",
			member.getNickname(), member.getId());
		long postId = jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);

		mockMvc.perform(get("/items/{id}/edit", postId))
			.andExpect(redirectedUrl("/login?required&next=/items/" + postId + "/edit"));
	}

	@Test
	void 로그인_화면을_오래_열어_두고_로그인하면_로그인_화면으로_돌아가서_안내한다() throws Exception {
		mockMvc.perform(post("/login").with(csrf().useInvalidToken())
				.param("loginId", "someone").param("password", "secret123")
				.header("Referer", "http://localhost/login"))
			.andExpect(redirectedUrl("/login?expired"));
		mockMvc.perform(get("/login").param("expired", ""))
			.andExpect(content().string(containsString("時間が経過したため、送信できませんでした。")));
	}

	@Test
	void 돌아갈_화면은_같은_사이트의_화면만이고_검색어_등은_그대로_둔다() throws Exception {
		mockMvc.perform(post("/signup").with(csrf().useInvalidToken()).header("Referer", "https://example.com/signup"))
			.andExpect(redirectedUrl("/?expired"));
		mockMvc.perform(post("/signup").with(csrf().useInvalidToken()))
			.andExpect(redirectedUrl("/?expired"));
		// 같은 사이트의 주소라도 경로가 「//」 로 시작하면 브라우저는 다른 사이트(evil.example)로 읽는다
		mockMvc.perform(post("/signup").with(csrf().useInvalidToken()).header("Referer", "http://localhost//evil.example/path"))
			.andExpect(redirectedUrl("/?expired"));
		mockMvc.perform(post("/signup").with(csrf().useInvalidToken())
				.header("Referer", "http://localhost/items?q=%E8%B2%A1%E5%B8%83&expired"))
			.andExpect(redirectedUrl("/items?q=%E8%B2%A1%E5%B8%83&expired"));
	}

	@Test
	void 로그아웃_버튼은_홈으로_보내고_화면의_폼이_아닌_요청은_403() throws Exception {
		mockMvc.perform(post("/logout").with(user(newMember())).with(csrf().useInvalidToken()))
			.andExpect(redirectedUrl("/"));

		// fetch 요청은 토큰을 헤더로 보낸다 (見つけた 비밀번호 확인)
		mockMvc.perform(post("/items/{id}/check-password", insertFound()).with(csrf().useInvalidToken().asHeader()).param("password", "1234"))
			.andExpect(status().isForbidden());
	}

	// ---------- 도우미 ----------

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("exp_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("期限の人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}

	private long insertFound() {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', '改札で拾いました。', '匿名の天使001', CURRENT_TIMESTAMP)");
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long insertLost(LoginMember owner) {
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-12][山手線][渋谷][その他] 傘', '2026-01-12', '山手線', '渋谷', 'その他', '傘', 'ホームで落としました。', ?, ?, CURRENT_TIMESTAMP)",
			owner.getNickname(), owner.getId());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);
	}

	private int countComments() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM POST_COMMENT", Integer.class);
	}
}
