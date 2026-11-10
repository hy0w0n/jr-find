package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

import tools.jackson.databind.json.JsonMapper;

// 로그인하지 않은 방문자가 글을 보기만 할 때는 세션을 만들지 않는다 (방문자·검색 로봇마다 서버 메모리에 세션이 쌓이지 않도록)
// CSRF 토큰을 화면에 넣으면 세션이 생기므로, 見つけた 비회원 글의 비밀번호 확인은 보내기 직전에 /csrf 에서 토큰을 받는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnonymousSessionTest {
	private static final String PASSWORD = "1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	@Test
	void 글을_보기만_하면_세션을_만들지_않는다() throws Exception {
		long guestPost = insertGuestPost();
		LoginMember member = newMember();
		long memberPost = insertMemberPost(member);
		long lostPost = insertLostPost(member);

		List<MockHttpServletRequestBuilder> pages = List.of(get("/"), get("/items"), get("/items/{id}", guestPost), get("/items/{id}", memberPost),
			get("/lost-items"), get("/lost-items/{id}", lostPost), get("/search").param("q", "傘"), get("/map"), get("/map/stations"));
		for (MockHttpServletRequestBuilder page : pages) {
			MvcResult result = mockMvc.perform(page).andExpect(status().isOk()).andReturn();
			String uri = result.getRequest().getRequestURI();
			assertThat(result.getRequest().getSession(false)).as(uri).isNull();
			assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).as(uri).doesNotContain("_csrf");
		}
	}

	@Test
	void 서버가_비밀번호_창을_연_화면에는_폼에_토큰을_넣는다() throws Exception {
		// JS 없이 「編集」「削除」를 누른 경우 (?verify=) 는 폼으로 보내므로 토큰이 필요하다
		long guestPost = insertGuestPost();

		String html = mockMvc.perform(get("/items/{id}", guestPost).param("verify", "delete"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).containsPattern("<form[^>]*id=\"pwForm\"[^>]*>\\s*<input type=\"hidden\" name=\"_csrf\"");
	}

	@Test
	void JS_는_보내기_직전에_토큰을_받아서_비밀번호를_확인하고_삭제한다() throws Exception {
		long guestPost = insertGuestPost();

		// 토큰 없이 보내면 거부
		mockMvc.perform(post("/items/{id}/check-password", guestPost).param("password", PASSWORD))
			.andExpect(status().isForbidden());

		// GET /csrf → { headerName, parameterName, token } (이때 세션이 생긴다)
		MvcResult csrf = mockMvc.perform(get("/csrf")).andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
		assertThat(session).isNotNull();
		Map<?, ?> token = JsonMapper.builder().build().readValue(csrf.getResponse().getContentAsString(), Map.class);
		assertThat(token.get("headerName")).isEqualTo("X-CSRF-TOKEN");
		assertThat(token.get("parameterName")).isEqualTo("_csrf");

		mockMvc.perform(post("/items/{id}/check-password", guestPost).session(session)
				.header((String) token.get("headerName"), token.get("token")).param("password", PASSWORD))
			.andExpect(jsonPath("$.ok").value(true));
		// 삭제 확인 창의 폼: JS 가 같은 토큰을 넣어서 보낸다
		mockMvc.perform(post("/items/{id}/delete", guestPost).session(session).param((String) token.get("parameterName"), (String) token.get("token")))
			.andExpect(redirectedUrl("/items"));
	}

	@Test
	void 작성자_본인의_화면에는_삭제_폼에_토큰을_넣는다() throws Exception {
		LoginMember member = newMember();
		long memberPost = insertMemberPost(member);

		String html = mockMvc.perform(get("/items/{id}", memberPost).with(user(member)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).containsPattern("<form[^>]*id=\"deleteForm\"[^>]*>\\s*<input type=\"hidden\" name=\"_csrf\"");
	}

	// ---------- 도우미 ----------

	private long insertGuestPost() {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, PASSWORD, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', '匿名の天使001', ?, CURRENT_TIMESTAMP)",
			new BCryptPasswordEncoder().encode(PASSWORD));
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long insertMemberPost(LoginMember member) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', ?, ?, CURRENT_TIMESTAMP)",
			member.getNickname(), member.getId());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long insertLostPost(LoginMember member) {
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-12][山手線][渋谷][その他] 傘', '2026-01-12', '山手線', '渋谷', 'その他', '傘', '本文', ?, ?, CURRENT_TIMESTAMP)",
			member.getNickname(), member.getId());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);
	}

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("anon_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("見る人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}
}
