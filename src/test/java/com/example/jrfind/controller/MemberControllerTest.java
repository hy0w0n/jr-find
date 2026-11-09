package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.service.MemberService;

// 회원가입·로그인과 회원 글의 수정·삭제 권한 테스트
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberControllerTest {
	private static final String PASSWORD = "secret123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	// ---------- 회원가입 ----------

	@Test
	void 가입하면_비밀번호는_해시로_저장되고_로그인_화면으로_이동한다() throws Exception {
		String loginId = newLoginId();

		mockMvc.perform(signupRequest("loginId", loginId, "nickname", "テスト太郎"))
			.andExpect(redirectedUrl("/login?registered"));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM MEMBER WHERE LOGINID = ?", loginId);
		assertThat((String) row.get("PASSWORD")).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(row.get("NICKNAME")).isEqualTo("テスト太郎");
		assertThat(row.get("AGREEDDATE")).isNotNull();
	}

	@Test
	void 닉네임을_비워_두면_氏名을_사용한다() throws Exception {
		String loginId = newLoginId();

		mockMvc.perform(signupRequest("loginId", loginId, "nickname", "", "realName", "山田花子"))
			.andExpect(redirectedUrl("/login?registered"));

		assertThat(jdbcTemplate.queryForObject("SELECT NICKNAME FROM MEMBER WHERE LOGINID = ?", String.class, loginId))
			.isEqualTo("山田花子");
	}

	@Test
	void 공백이나_보이지_않는_글자만_있는_이름은_입력하지_않은_것으로_본다() throws Exception {
		mockMvc.perform(signupRequest("realName", "　\u200b "))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("signupForm", "realName"));

		// 닉네임은 비운 것과 같으므로 氏名 을 사용 (氏名 앞뒤의 전각 공백도 지운다)
		String loginId = newLoginId();
		mockMvc.perform(signupRequest("loginId", loginId, "nickname", "　　", "realName", "　山田花子　"))
			.andExpect(redirectedUrl("/login?registered"));
		assertThat(jdbcTemplate.queryForObject("SELECT NICKNAME FROM MEMBER WHERE LOGINID = ?", String.class, loginId))
			.isEqualTo("山田花子");
	}

	@Test
	void 사용할_수_없는_이름은_공백이나_보이지_않는_글자를_섞어도_쓸_수_없다() throws Exception {
		for (String nickname : List.of("　退会したユーザー", "退会したユーザー　", "退会した　ユーザー", "退会した\u200bユーザー", "\u00a0匿名の天使 1")) {
			mockMvc.perform(signupRequest("nickname", nickname))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("signupForm", "nickname", "reserved"));
		}
	}

	@Test
	void 대소문자만_다른_ID로는_가입할_수_없다() throws Exception {
		String loginId = signup();

		mockMvc.perform(signupRequest("loginId", loginId.toUpperCase()))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "loginId", "duplicate"));
	}

	@Test
	void 가입_입력값_오류는_항목별로_돌려준다() throws Exception {
		String loginId = newLoginId();
		mockMvc.perform(signupRequest("loginId", loginId)).andExpect(redirectedUrl("/login?registered"));

		mockMvc.perform(signupRequest("loginId", loginId)) // 이미 사용 중인 ID
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "loginId", "duplicate"));
		mockMvc.perform(signupRequest("loginId", newLoginId(), "passwordConfirm", "different1"))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "passwordConfirm", "mismatch"));
		mockMvc.perform(signupRequest("loginId", newLoginId(), "agree", "false"))
			.andExpect(model().attributeHasFieldErrors("signupForm", "agree"));
		mockMvc.perform(signupRequest("loginId", newLoginId(), "password", "onlyletters", "passwordConfirm", "onlyletters"))
			.andExpect(model().attributeHasFieldErrors("signupForm", "password"));
		mockMvc.perform(signupRequest("loginId", newLoginId(), "nickname", "匿名の天使 001"))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "nickname", "reserved"));
		mockMvc.perform(signupRequest("loginId", newLoginId(), "nickname", " 退会したユーザー "))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "nickname", "reserved"));
		mockMvc.perform(signupRequest("loginId", "ab")) // 4자 미만
			.andExpect(model().attributeHasFieldErrors("signupForm", "loginId"));
	}

	// ---------- 로그인 / 로그아웃 ----------

	@Test
	void 가입한_ID와_비밀번호로_로그인할_수_있다() throws Exception {
		String loginId = signup();

		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(PASSWORD))
			.andExpect(authenticated().withUsername(loginId))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void 비밀번호가_틀리거나_없는_ID면_로그인_실패() throws Exception {
		String loginId = signup();

		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password("wrong1234"))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
		mockMvc.perform(formLogin("/login").userParameter("loginId").user("nobody_" + loginId).password(PASSWORD))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void 로그인하면_헤더에_닉네임과_로그아웃이_보이고_로그아웃하면_돌아간다() throws Exception {
		LoginMember member = loginMember(signup());

		mockMvc.perform(get("/items").with(user(member)))
			.andExpect(content().string(containsString(member.getNickname() + " さん")))
			.andExpect(content().string(containsString("ログアウト")));
		mockMvc.perform(get("/login").with(user(member)))
			.andExpect(redirectedUrl("/"));

		// 세션 쿠키와 브라우저에 저장된 화면을 지운다 (로그아웃 후 「뒤로」로 개인정보 화면이 보이지 않도록)
		mockMvc.perform(post("/logout").with(user(member)).with(csrf()))
			.andExpect(redirectedUrl("/?logout"))
			.andExpect(unauthenticated())
			.andExpect(cookie().maxAge("JSESSIONID", 0))
			.andExpect(header().string("Clear-Site-Data", "\"cache\""));
		mockMvc.perform(get("/").param("logout", ""))
			.andExpect(content().string(containsString("ログアウトしました。")));
	}

	// ---------- 로그인 연속 실패 제한 ----------
	// 테스트끼리 횟수가 섞이지 않도록 매번 다른 ID 와 접속 IP 를 쓴다

	@Test
	void 같은_ID로_5번_틀리면_맞는_비밀번호로도_로그인할_수_없다() throws Exception {
		String loginId = signup();
		String ip = newIp();

		for (int i = 1; i <= 4; i++) {
			mockMvc.perform(loginRequest(loginId, "wrong1234", ip))
				.andExpect(redirectedUrl("/login?error"));
		}
		mockMvc.perform(loginRequest(loginId, "wrong1234", ip))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?locked"));
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?locked"));

		mockMvc.perform(get("/login").param("locked", ""))
			.andExpect(content().string(containsString("一時的にログインを制限しています")));
	}

	@Test
	void ID_앞뒤에_공백을_붙여도_같은_ID로_세어_잠근다() throws Exception {
		String loginId = signup();
		String ip = newIp();
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(loginRequest(loginId, "wrong1234", ip));
		}

		// Spring Security 는 앞뒤 공백을 지우고 확인하므로, 공백을 붙인 ID 도 잠겨 있어야 한다
		mockMvc.perform(loginRequest(loginId + " ", PASSWORD, ip))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?locked"));
		mockMvc.perform(loginRequest(" " + loginId, PASSWORD, ip))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?locked"));
	}

	@Test
	void 다른_곳에서_접속한_주인은_잠기지_않는다() throws Exception {
		String loginId = signup();
		String attackerIp = newIp();
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(loginRequest(loginId, "wrong1234", attackerIp));
		}

		mockMvc.perform(loginRequest(loginId, PASSWORD, newIp()))
			.andExpect(authenticated().withUsername(loginId))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void 로그인에_성공하면_틀린_횟수가_지워진다() throws Exception {
		String loginId = signup();
		String ip = newIp();
		for (int i = 1; i <= 4; i++) {
			mockMvc.perform(loginRequest(loginId, "wrong1234", ip));
		}
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip))
			.andExpect(authenticated());

		for (int i = 1; i <= 4; i++) {
			mockMvc.perform(loginRequest(loginId, "wrong1234", ip))
				.andExpect(redirectedUrl("/login?error"));
		}
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip))
			.andExpect(authenticated());
	}

	@Test
	void ID를_바꿔_가며_20번_틀리면_그_IP에서는_로그인할_수_없다() throws Exception {
		String loginId = signup();
		String ip = newIp();

		// 없는 ID 도 똑같이 센다 (잠금 여부로 ID 가 있는지 알 수 없게)
		for (int i = 1; i <= 19; i++) {
			mockMvc.perform(loginRequest("nobody_" + i + "_" + loginId, PASSWORD, ip))
				.andExpect(redirectedUrl("/login?error"));
		}
		// 자기 계정에 로그인해도 IP 단위 횟수는 지워지지 않는다
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip))
			.andExpect(authenticated());
		mockMvc.perform(loginRequest("nobody_20_" + loginId, PASSWORD, ip))
			.andExpect(redirectedUrl("/login?locked"));
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?locked"));

		mockMvc.perform(loginRequest(loginId, PASSWORD, newIp()))
			.andExpect(authenticated());
	}

	@Test
	void 로그인하면_원래_가려던_화면으로_돌아간다() throws Exception {
		String loginId = signup();

		MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/mypage"))
			.andExpect(redirectedUrl("/login?required"))
			.andReturn().getRequest().getSession();

		mockMvc.perform(loginRequest(loginId, PASSWORD, newIp()).session(session))
			.andExpect(authenticated())
			.andExpect(redirectedUrl("http://localhost/mypage"));
	}

	@Test
	void 한_번_틀린_뒤에_로그인해도_원래_가려던_화면으로_돌아간다() throws Exception {
		String loginId = signup();
		String ip = newIp();
		MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/mypage"))
			.andReturn().getRequest().getSession();

		mockMvc.perform(get("/login").param("required", "").session(session)).andExpect(status().isOk());
		mockMvc.perform(loginRequest(loginId, "wrong1234", ip).session(session)).andExpect(redirectedUrl("/login?error"));
		mockMvc.perform(get("/login").param("error", "").session(session)).andExpect(status().isOk());
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip).session(session))
			.andExpect(redirectedUrl("http://localhost/mypage"));
	}

	@Test
	void 로그인_화면을_직접_열어서_로그인하면_예전에_막혔던_화면으로_가지_않는다() throws Exception {
		String loginId = signup();
		MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/mypage/withdraw"))
			.andExpect(redirectedUrl("/login?required"))
			.andReturn().getRequest().getSession();

		// 로그인하지 않고 다른 화면을 보다가, 나중에 헤더의 「ログイン」으로 로그인
		mockMvc.perform(get("/items").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/login").session(session)).andExpect(status().isOk());
		mockMvc.perform(loginRequest(loginId, PASSWORD, newIp()).session(session))
			.andExpect(authenticated())
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void 헤더의_ログイン으로_로그인하면_보던_화면으로_돌아간다() throws Exception {
		String loginId = signup();
		String ip = newIp();

		// 헤더의 링크에는 지금 화면 주소가 붙는다 (로그인·가입 화면에서는 붙이지 않음)
		mockMvc.perform(get("/items/{id}", 1))
			.andExpect(content().string(containsString("href=\"/login?next=")));
		mockMvc.perform(get("/signup"))
			.andExpect(content().string(not(containsString("/login?next="))));
		mockMvc.perform(get("/login").param("next", "/items/1"))
			.andExpect(content().string(containsString("name=\"next\" value=\"/items/1\"")));

		// 틀리면 돌아갈 화면을 넘긴 채 로그인 화면으로, 맞으면 그 화면으로
		mockMvc.perform(loginRequest(loginId, "wrong1234", ip).param("next", "/items/1"))
			.andExpect(redirectedUrl("/login?error&next=%2Fitems%2F1"));
		mockMvc.perform(loginRequest(loginId, PASSWORD, ip).param("next", "/items/1"))
			.andExpect(authenticated())
			.andExpect(redirectedUrl("/items/1"));
	}

	@Test
	void 돌아갈_화면이_이_사이트_밖이거나_로그인_화면이면_홈으로_간다() throws Exception {
		String loginId = signup();
		for (String next : List.of("https://example.com/", "//example.com/", "/\\example.com", "/login?error", "/logout")) {
			mockMvc.perform(loginRequest(loginId, PASSWORD, newIp()).param("next", next))
				.andExpect(authenticated())
				.andExpect(redirectedUrl("/"));
		}
	}

	@Test
	void 로그인에_실패해도_입력한_ID는_다시_채운다() throws Exception {
		String loginId = signup();

		MockHttpSession session = (MockHttpSession) mockMvc.perform(loginRequest(loginId, "wrong1234", newIp()))
			.andExpect(redirectedUrl("/login?error"))
			.andReturn().getRequest().getSession();
		mockMvc.perform(get("/login").param("error", "").session(session))
			.andExpect(content().string(containsString("value=\"" + loginId + "\"")));
	}

	@Test
	void 빈_칸에는_형식_오류가_아니라_입력_안내만_보여준다() throws Exception {
		mockMvc.perform(signupRequest("loginId", "", "password", " ", "phone", ""))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "loginId", "NotBlank"))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "password", "NotBlank"))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "phone", "NotBlank"))
			.andExpect(content().string(containsString("IDを入力してください。")))
			.andExpect(content().string(not(containsString("IDは半角英数字と _ で4〜20文字"))))
			.andExpect(content().string(not(containsString("電話番号の形式が正しくありません"))));
	}

	@Test
	void 전화번호는_전각으로_입력해도_받고_자리수가_맞지_않으면_안내한다() throws Exception {
		String loginId = newLoginId();
		mockMvc.perform(signupRequest("loginId", loginId, "phone", "０９０－１２３４－５６７８"))
			.andExpect(redirectedUrl("/login?registered"));
		assertThat(jdbcTemplate.queryForObject("SELECT PHONE FROM MEMBER WHERE LOGINID = ?", String.class, loginId))
			.isEqualTo("090-1234-5678");

		mockMvc.perform(signupRequest("phone", "03ー1234ー5678")) // 하이픈 대신 장음 기호(ー)
			.andExpect(redirectedUrl("/login?registered"));
		for (String phone : List.of("12-34-567", "090-1234-56789", "1234567890")) {
			mockMvc.perform(signupRequest("phone", phone))
				.andExpect(model().attributeHasFieldErrors("signupForm", "phone"));
		}
	}

	@Test
	void 생년월일이_너무_오래되었거나_날짜가_아니면_안내한다() throws Exception {
		mockMvc.perform(signupRequest("birthDate", "0001-01-01"))
			.andExpect(model().attributeHasFieldErrorCode("signupForm", "birthDate", "tooOld"));
		// 날짜로 바꿀 수 없는 값: Spring 의 영어 원문 대신 일본어 안내 (messages.properties)
		mockMvc.perform(signupRequest("birthDate", "1990/01/01"))
			.andExpect(content().string(containsString("日付を正しく入力してください。")))
			.andExpect(content().string(not(containsString("Failed to convert"))));
	}

	// ---------- 회원 글 ----------

	@Test
	void 회원은_비밀번호_없이_닉네임으로_글을_쓴다() throws Exception {
		LoginMember member = loginMember(signup());

		mockMvc.perform(newItemRequest().with(user(member)))
			.andExpect(redirectedUrl("/items"));

		Map<String, Object> row = latestItem();
		assertThat(row.get("AUTHOR")).isEqualTo(member.getNickname());
		assertThat(row.get("MEMBERID")).isEqualTo(member.getId());
		assertThat(row.get("PASSWORD")).isNull();
	}

	@Test
	void 회원_글은_작성자_본인만_수정_삭제할_수_있다() throws Exception {
		LoginMember owner = loginMember(signup());
		LoginMember other = loginMember(signup());
		mockMvc.perform(newItemRequest().with(user(owner)));
		long id = (Long) latestItem().get("ID");

		// 상세 화면: 본인에게만 버튼, 비밀번호 모달은 쓰지 않음
		mockMvc.perform(get("/items/{id}", id).with(user(owner)))
			.andExpect(content().string(containsString("onclick=\"goEdit()\"")))
			.andExpect(content().string(not(containsString("openPwModal('delete')"))));
		mockMvc.perform(get("/items/{id}", id).with(user(other)))
			.andExpect(content().string(not(containsString("onclick=\"goEdit()\""))))
			.andExpect(content().string(not(containsString("openPwModal('delete')"))));

		// 다른 회원·비로그인: 수정 화면은 상세로 돌려보내고, 삭제는 403
		mockMvc.perform(get("/items/{id}/edit", id).with(user(other)))
			.andExpect(redirectedUrl("/items/" + id));
		mockMvc.perform(post("/items/{id}/delete", id).with(user(other)).with(csrf()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/items/{id}/delete", id).with(csrf()))
			.andExpect(status().isForbidden());
		// 회원 글에는 4자리 비밀번호가 없으므로 비밀번호 확인도 통과하지 않는다
		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).param("password", "1234"))
			.andExpect(content().string("false"));

		// 본인: 비밀번호 없이 수정 화면 → 삭제
		mockMvc.perform(get("/items/{id}/edit", id).with(user(owner)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/items/{id}/delete", id).with(user(owner)).with(csrf()))
			.andExpect(redirectedUrl("/items"));
		mockMvc.perform(get("/items/{id}", id))
			.andExpect(status().isNotFound());
	}

	// ---------- 도우미 ----------

	private String newLoginId() {
		return "user_" + UUID.randomUUID().toString().substring(0, 8);
	}

	// 정상 입력값으로 채운 가입 요청. overrides 는 "항목명", "값" 순서로 넘기면 해당 값만 바꾼다.
	private MockHttpServletRequestBuilder signupRequest(String... overrides) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("loginId", newLoginId());
		params.put("password", PASSWORD);
		params.put("passwordConfirm", PASSWORD);
		params.put("realName", "山田太郎");
		params.put("nickname", "やまだ");
		params.put("birthDate", "2000-04-16");
		params.put("phone", "090-1234-5678");
		params.put("agree", "true");
		for (int i = 0; i < overrides.length; i += 2) {
			params.put(overrides[i], overrides[i + 1]);
		}

		MockHttpServletRequestBuilder request = post("/signup").with(csrf());
		params.forEach((name, value) -> request.param(name, value));
		return request;
	}

	private String signup() throws Exception {
		String loginId = newLoginId();
		mockMvc.perform(signupRequest("loginId", loginId)).andExpect(redirectedUrl("/login?registered"));
		return loginId;
	}

	// 접속 IP 를 지정한 로그인 요청 (POST /login)
	private MockHttpServletRequestBuilder loginRequest(String loginId, String password, String ip) {
		return post("/login")
			.param("loginId", loginId)
			.param("password", password)
			.with(csrf())
			.with(request -> {
				request.setRemoteAddr(ip);
				return request;
			});
	}

	private String newIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	private LoginMember loginMember(String loginId) {
		return (LoginMember) memberService.loadUserByUsername(loginId);
	}

	private MockMultipartHttpServletRequestBuilder newItemRequest() {
		MockMultipartHttpServletRequestBuilder request = multipart("/items/new");
		request.with(csrf());
		request.param("foundDate", "2026-01-10")
			.param("lineName", "山手線")
			.param("stationName", "新宿")
			.param("categoryName", "財布")
			.param("itemName", "黄色い財布")
			.param("contents", "5号車で見つけました。");
		return request;
	}

	private Map<String, Object> latestItem() {
		return jdbcTemplate.queryForMap("SELECT * FROM FOUND_ITEM ORDER BY ID DESC LIMIT 1");
	}
}
