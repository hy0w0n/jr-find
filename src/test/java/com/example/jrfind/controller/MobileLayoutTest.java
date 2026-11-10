package com.example.jrfind.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 휴대폰 화면: 모든 화면이 기기 폭에 맞춰 보이고(viewport), 공통 휴대폰 스타일(mobile.css)을 불러온다
// (실제 배치는 브라우저로 확인. viewport 가 없으면 PC 화면이 40% 크기로 줄어 보였다)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileLayoutTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberService memberService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long insertFoundItem() {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][その他] 傘', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', '匿名の天使001', CURRENT_TIMESTAMP)");
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	@Test
	void 모든_화면은_기기_폭에_맞추고_휴대폰_스타일을_불러온다() throws Exception {
		for (String url : List.of("/", "/items", "/items/" + insertFoundItem(), "/items/new", "/lost-items", "/search?q=財布", "/map", "/login", "/signup")) {
			expectMobileReady(url, null);
		}
		LoginMember member = newMember();
		for (String url : List.of("/lost-items/new", "/mypage", "/mypage/edit", "/mypage/password", "/mypage/withdraw")) {
			expectMobileReady(url, member);
		}
	}

	@Test
	void 휴대폰_스타일은_폭_768px_이하에서만_적용된다() throws Exception {
		mockMvc.perform(get("/css/mobile.css"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("@media (max-width: 768px)")))
			.andExpect(header().string("Cache-Control", "max-age=31536000, public")); // 정적 파일은 1년 동안 다시 받지 않는다
	}

	private void expectMobileReady(String url, LoginMember member) throws Exception {
		var request = get(url);
		if (member != null) {
			request.with(user(member));
		}
		mockMvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")))
			// 주소에는 파일 내용의 해시가 붙는다 (/css/mobile-<해시>.css. 고치면 주소가 바뀌어 브라우저가 새로 받는다)
			.andExpect(content().string(matchesPattern("(?s).*<link rel=\"stylesheet\" href=\"/css/mobile-[0-9a-f]{32}\\.css\">.*")));
	}

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("mob_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("スマホの人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}
}
