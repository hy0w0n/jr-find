package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 모든 응답에 붙는 보안 헤더, 개발용 기능(H2 콘솔)의 기본 설정, 화면이 CSP 를 지킬 수 있는지
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest {
	// 속성이 없는 <script> (인라인 스크립트), on… 속성 (onclick 등)
	private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>");
	private static final Pattern EVENT_ATTRIBUTE = Pattern.compile("\\son[a-z]+=\"");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	@Test
	void 화면에는_보안_헤더가_붙는다() throws Exception {
		mockMvc.perform(get("/items"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Security-Policy",
				"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tile.openstreetmap.org;"
					+ " connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
			.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
			.andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
			.andExpect(header().string("X-Frame-Options", "DENY")) // 다른 사이트가 이 화면을 iframe 에 넣지 못하게
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void H2_콘솔은_기본으로_꺼져_있다() throws Exception {
		mockMvc.perform(get("/h2-console/"))
			.andExpect(status().isNotFound());
	}

	@Test
	void 모든_화면에_인라인_스크립트와_on_속성이_없고_제목_h1이_하나다() throws Exception {
		// CSP 의 script-src 'self' 로 막히는 것이 없어야 한다 (막히면 그 기능이 동작하지 않는다)
		LoginMember member = newMember();
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, PASSWORD, REGISTRATIONDATE)"
				+ " VALUES ('t', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', '匿名の天使 001', 'x', CURRENT_TIMESTAMP)");
		long foundId = jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('t', '2026-01-12', '山手線', '渋谷', 'その他', '傘', '本文', ?, ?, CURRENT_TIMESTAMP)", member.getNickname(), member.getId());
		long lostId = jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);

		List<MockHttpServletRequestBuilder> pages = List.of(
			get("/"), get("/items"), get("/items/new"), get("/items/{id}", foundId), get("/items/{id}", foundId).param("verify", "edit"),
			get("/lost-items"), get("/lost-items/{id}", lostId).with(user(member)), get("/lost-items/new").with(user(member)),
			get("/lost-items/{id}/edit", lostId).with(user(member)), get("/search").param("q", "傘"), get("/map"),
			get("/login"), get("/signup"), get("/mypage").with(user(member)), get("/mypage/edit").with(user(member)),
			get("/mypage/password").with(user(member)), get("/mypage/withdraw").with(user(member)));
		for (MockHttpServletRequestBuilder page : pages) {
			String html = mockMvc.perform(page).andReturn().getResponse().getContentAsString();
			String url = page.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();
			assertThat(INLINE_SCRIPT.matcher(html).find()).as("inline script: " + url).isFalse();
			assertThat(EVENT_ATTRIBUTE.matcher(html).find()).as("on… attribute: " + url).isFalse();
			assertThat(html.split("<h1", -1)).as("one h1: " + url).hasSize(2);
		}
	}

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("csp_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("画面の人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}
}
