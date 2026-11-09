package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.jrfind.service.ImageStorage;

// 게시글 등록·수정·삭제와 비밀번호 확인 흐름 (보안 수정 사항 위주) 테스트
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JrfindControllerTest {
	private static final String PASSWORD = "1234";
	private static final byte[] PNG_HEADER = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ImageStorage imageStorage;

	@Test
	void 등록하면_비밀번호는_해시로_저장되고_제목은_서버에서_만든다() throws Exception {
		mockMvc.perform(newItemRequest()
				.param("title", "<script>hacked</script>")  // 클라이언트가 보낸 제목은 무시
				.param("imgPath", "../../etc/passwd"))      // 이미지 경로 직접 지정도 무시
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/items"));

		Map<String, Object> row = latestRow();
		assertThat(row.get("TITLE")).isEqualTo("[2026-01-10][山手線][新宿][財布] 黄色い財布");
		assertThat((String) row.get("PASSWORD")).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(row.get("IMGPATH")).isNull();
	}

	@Test
	void 비밀번호_확인_없이_삭제하면_403() throws Exception {
		long id = createItem();

		mockMvc.perform(post("/items/{id}/delete", id).with(csrf()))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/items/{id}", id))
			.andExpect(status().isOk());
	}

	@Test
	void 비밀번호를_틀리면_삭제할_수_없다() throws Exception {
		long id = createItem();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).session(session).param("password", "0000"))
			.andExpect(jsonPath("$.ok").value(false));
		mockMvc.perform(post("/items/{id}/delete", id).with(csrf()).session(session))
			.andExpect(status().isForbidden());
	}

	@Test
	void 비밀번호_확인_후_삭제하면_이후_상세_화면은_404() throws Exception {
		long id = createItem();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).session(session).param("password", PASSWORD))
			.andExpect(jsonPath("$.ok").value(true));
		mockMvc.perform(post("/items/{id}/delete", id).with(csrf()).session(session))
			.andExpect(redirectedUrl("/items"));

		mockMvc.perform(get("/items/{id}", id))
			.andExpect(status().isNotFound());
		// 다른 탭에 남아 있던 화면에서 비밀번호를 넣어도 「틀렸다」가 아니라 없는 글 (틀린 횟수로 세지 않아 잠기지도 않는다)
		for (int i = 0; i < 6; i++) {
			mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).param("password", PASSWORD))
				.andExpect(status().isNotFound());
		}
	}

	@Test
	void 수정_화면은_비밀번호_확인이_필요하고_비밀번호를_HTML에_내보내지_않는다() throws Exception {
		long id = createItem();
		String hash = (String) latestRow().get("PASSWORD");

		// 확인 없이 URL로 직접 접근 → 상세 화면으로
		mockMvc.perform(get("/items/{id}/edit", id))
			.andExpect(redirectedUrl("/items/" + id));

		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).session(session).param("password", PASSWORD));
		mockMvc.perform(get("/items/{id}/edit", id).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(hash))))
			.andExpect(content().string(not(containsString("name=\"password\""))));
	}

	@Test
	void 아무것도_바꾸지_않고_저장하면_編集済み가_붙지_않고_너무_오래된_날짜는_저장하지_않는다() throws Exception {
		long id = createItem();
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).session(session).param("password", PASSWORD));

		// 등록할 때와 같은 값 그대로 저장
		mockMvc.perform(multipart("/items/{id}/edit", id).with(csrf()).session(session)
				.param("foundDate", "2026-01-10").param("lineName", "山手線").param("stationName", "新宿")
				.param("categoryName", "財布").param("itemName", "黄色い財布").param("contents", "5号車で見つけました。"))
			.andExpect(redirectedUrl("/items/" + id));
		assertThat(latestRow().get("MODIFIEDDATE")).isNull();

		int before = countItems();
		mockMvc.perform(newItemRequest("foundDate", "1999-12-31"))
			.andExpect(model().attribute("errorMessage", "発見した日は2000年1月1日以降の日付を入力してください。"));
		assertThat(countItems()).isEqualTo(before);
	}

	@Test
	void 비밀번호_확인_없이_수정_요청하면_저장하지_않고_비밀번호를_다시_물은_뒤_입력한_내용을_채운다() throws Exception {
		long id = createItem();
		// 다른 탭에서 먼저 저장했거나, 저장한 뒤 「뒤로」로 돌아와 다시 보낸 경우와 같다 (저장하면 비밀번호 확인이 풀린다)
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(multipart("/items/{id}/edit", id).with(csrf()).session(session)
				.param("foundDate", "2026-01-10")
				.param("lineName", "山手線")
				.param("stationName", "新宿")
				.param("categoryName", "財布")
				.param("itemName", "書き換え")
				.param("contents", "書き換えた本文"))
			.andExpect(redirectedUrl("/items/" + id + "?reverify"));
		assertThat(latestRow().get("ITEMNAME")).isEqualTo("黄色い財布");

		// 상세 화면에서 비밀번호 창을 바로 연다
		mockMvc.perform(get("/items/{id}", id).param("reverify", "").session(session))
			.andExpect(model().attribute("reverify", true));
		// 다시 확인하면 수정 화면에 입력한 내용이 채워져 있다 (한 번만)
		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).session(session).param("password", PASSWORD));
		mockMvc.perform(get("/items/{id}/edit", id).session(session))
			.andExpect(content().string(containsString("value=\"書き換え\"")))
			.andExpect(content().string(containsString(">書き換えた本文</textarea>")))
			.andExpect(model().attribute("errorMessage", "入力していた内容を元に戻しました。内容を確認して、もう一度送信してください。"));
		mockMvc.perform(get("/items/{id}/edit", id).session(session))
			.andExpect(content().string(containsString("value=\"黄色い財布\"")));

		// 비밀번호를 확인하지 않은 다른 사람이 상세 화면에 ?reverify 를 붙여도 창은 열리지 않는다
		mockMvc.perform(get("/items/{id}", id).param("reverify", ""))
			.andExpect(model().attribute("reverify", false));
	}

	@Test
	void 비밀번호를_연속으로_틀리면_잠기고_잠기기_전에_남은_횟수를_알려준다() throws Exception {
		long id = createItem();

		for (int remaining = 4; remaining >= 1; remaining--) {
			mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).param("password", "0000"))
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.remaining").value(remaining))
				.andExpect(jsonPath("$.lockMinutes").value(5));
		}
		// 5번째로 틀리면 그 자리에서 잠겼다고 알린다
		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).param("password", "0000"))
			.andExpect(status().isTooManyRequests());
		// 잠긴 동안에는 맞는 비밀번호도 거부
		mockMvc.perform(post("/items/{id}/check-password", id).with(csrf()).param("password", PASSWORD))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void 이미지가_아닌_파일은_저장하지_않는다() throws Exception {
		int before = countItems();
		MockMultipartFile html = new MockMultipartFile("imageFile", "evil.png", "image/png", "<html><script>alert(1)</script></html>".getBytes());

		mockMvc.perform(newItemRequest().file(html))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage",
				"画像ファイル(JPG・PNG・GIF・WEBP)のみアップロードできます。\n(選択した画像は、もう一度選択してください。)"));

		assertThat(countItems()).isEqualTo(before);
	}

	@Test
	void 입력_오류로_등록_화면을_다시_보여줄_때_입력한_값을_유지한다() throws Exception {
		int before = countItems();

		mockMvc.perform(newItemRequest("password", "12ab", "itemName", "青いハンカチ", "contents", "改札の近くにありました。"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage", "パスワードは半角数字4桁で入力してください。"))
			.andExpect(content().string(containsString("value=\"2026-01-10\"")))
			.andExpect(content().string(containsString("<option value=\"山手線\" selected=\"selected\">")))
			.andExpect(content().string(containsString("<option value=\"財布\" selected=\"selected\">")))
			.andExpect(content().string(containsString("value=\"青いハンカチ\"")))
			.andExpect(content().string(containsString(">改札の近くにありました。</textarea>")))
			.andExpect(content().string(not(containsString("value=\"12ab\"")))); // 비밀번호는 다시 채우지 않음

		// 본문의 줄바꿈은 1글자로 센다 (화면 기준 500자)
		mockMvc.perform(newItemRequest("contents", ("あ".repeat(49) + "\r\n").repeat(10)))
			.andExpect(redirectedUrl("/items"));
		assertThat(countItems()).isEqualTo(before + 1);
	}

	@Test
	void 업로드_파일명의_경로는_무시하고_새_이름으로_저장한다() throws Exception {
		MockMultipartFile png = new MockMultipartFile("imageFile", "../../evil.png", "image/png", PNG_HEADER);

		mockMvc.perform(newItemRequest().file(png))
			.andExpect(redirectedUrl("/items"));

		String storedName = (String) latestRow().get("IMGPATH");
		assertThat(storedName).matches("\\d{14}_[0-9a-f]{8}\\.png");
		assertThat(Files.exists(imageStorage.getUploadDir().resolve(storedName))).isTrue();
		assertThat(Path.of(storedName).getParent()).isNull();
	}

	@Test
	void 목록에_없는_역이나_미래_날짜는_저장하지_않는다() throws Exception {
		int before = countItems();

		mockMvc.perform(newItemRequest("stationName", "横浜")) // 山手線에 없는 역
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage", "発見した場所(路線・駅)を正しく選択してください。"));
		mockMvc.perform(newItemRequest("foundDate", LocalDate.now().plusDays(1).toString()))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage", "未来の日付は入力できません。"));
		mockMvc.perform(newItemRequest("password", "abcd"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage", "パスワードは半角数字4桁で入力してください。"));

		assertThat(countItems()).isEqualTo(before);
	}

	@Test
	void 같은_내용을_연달아_두_번_등록해도_한_번만_저장한다() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String itemName = "二度押しの財布" + System.nanoTime() % 10000;
		int before = countItems();

		// 저장하지 못한 경우(목록에 없는 역)는 고쳐서 다시 보낼 수 있다
		mockMvc.perform(newItemRequest("itemName", itemName, "stationName", "横浜").session(session)).andExpect(status().isOk());
		mockMvc.perform(newItemRequest("itemName", itemName, "stationName", "横浜").session(session)).andExpect(status().isOk());
		// 등록 → 「뒤로」로 돌아온 폼(브라우저가 입력한 내용을 다시 채운다)을 그대로 다시 보냄: 처음처럼 목록으로 가지만 저장은 한 번
		mockMvc.perform(newItemRequest("itemName", itemName).session(session)).andExpect(redirectedUrl("/items"));
		mockMvc.perform(newItemRequest("itemName", itemName).session(session)).andExpect(redirectedUrl("/items"));
		assertThat(countItems()).isEqualTo(before + 1);

		// 내용이 다르거나, 다른 접속자면 저장한다
		mockMvc.perform(newItemRequest("itemName", itemName, "contents", "別の本文").session(session)).andExpect(redirectedUrl("/items"));
		mockMvc.perform(newItemRequest("itemName", itemName)).andExpect(redirectedUrl("/items"));
		assertThat(countItems()).isEqualTo(before + 3);
	}

	@Test
	void 날짜_선택_라이브러리는_외부_CDN이_아니라_이_앱에서_제공한다() throws Exception {
		// CDN 이 막히면 등록 화면의 스크립트가 멈춰서 글을 쓸 수 없었다 (WebJars)
		mockMvc.perform(get("/items/new"))
			.andExpect(content().string(containsString("src=\"/webjars/flatpickr/")))
			.andExpect(content().string(not(containsString("cdn.jsdelivr.net"))));
		for (String path : List.of("/webjars/flatpickr/dist/flatpickr.min.js", "/webjars/flatpickr/dist/l10n/ja.js", "/webjars/flatpickr/dist/flatpickr.min.css")) {
			mockMvc.perform(get(path)).andExpect(status().isOk());
		}
	}

	@Test
	void 보이지_않는_글자만_있는_물건_이름은_받지_않고_글자_방향을_바꾸는_문자는_지운다() throws Exception {
		int before = countItems();
		for (String invisible : List.of("\u200b", "\u00a0\u3000", "\u202e")) {
			mockMvc.perform(newItemRequest("itemName", invisible))
				.andExpect(model().attribute("errorMessage", "具体的な物を入力してください。"));
			mockMvc.perform(newItemRequest("contents", invisible))
				.andExpect(model().attribute("errorMessage", "詳細を入力してください。"));
		}
		assertThat(countItems()).isEqualTo(before);

		// U+202E 를 넣으면 목록에서 글자가 거꾸로 보였다
		mockMvc.perform(newItemRequest("itemName", "\u202eレイフサ", "contents", "本文\u2066です")).andExpect(redirectedUrl("/items"));
		assertThat(latestRow().get("ITEMNAME")).isEqualTo("レイフサ");
		assertThat(latestRow().get("CONTENTS")).isEqualTo("本文です");
	}

	@Test
	void 목록의_글_정렬_페이지_필터는_스크립트_없이_동작하는_링크와_폼이다() throws Exception {
		String itemName = "リンクの財布" + System.nanoTime() % 10000;
		mockMvc.perform(newItemRequest("itemName", itemName)).andExpect(redirectedUrl("/items"));
		long id = (Long) latestRow().get("ID");

		// 키보드(Tab·Enter), 가운데 클릭으로 새 탭, JS 를 끈 브라우저에서도 열 수 있도록
		String html = mockMvc.perform(get("/items").param("line", "山手線").param("sort", "date_asc").param("q", itemName))
			.andExpect(content().string(containsString("<a class=\"row-link\" href=\"/items/" + id + "\"")))
			.andExpect(content().string(containsString("aria-sort=\"ascending\"")))
			.andExpect(content().string(containsString("<label for=\"lineFilter\">路線</label>")))
			.andExpect(content().string(containsString("<select class=\"filter-select\" id=\"lineFilter\" name=\"line\" form=\"filterForm\">")))
			.andExpect(content().string(containsString("<option value=\"山手線\" selected=\"selected\">山手線</option>")))
			.andExpect(content().string(containsString("<nav class=\"pager\" aria-label=\"ページ\">")))
			.andReturn().getResponse().getContentAsString();
		assertThat(html).doesNotContain("onclick").doesNotContain("<script>");

		// 상세 화면의 「一覧」은 보던 목록(필터·페이지 그대로)으로
		mockMvc.perform(get("/items/{id}", id).header("Referer", "http://localhost/items?line=%E5%B1%B1%E6%89%8B%E7%B7%9A&page=2"))
			.andExpect(content().string(containsString("href=\"/items?line=%E5%B1%B1%E6%89%8B%E7%B7%9A&amp;page=2\" class=\"btn-list-wrapper\"")));
	}

	@Test
	void 등록_화면의_입력_칸에는_이름이_있고_인라인_스크립트가_없다() throws Exception {
		String html = mockMvc.perform(get("/items/new")).andReturn().getResponse().getContentAsString();
		assertThat(html)
			.contains("<label class=\"form-label\" for=\"datePicker\">発見した日</label>")
			.contains("aria-label=\"発見した路線\"", "aria-label=\"発見した駅\"", "aria-label=\"具体的な物\"", "aria-label=\"写真を選ぶ（任意）\"")
			.contains("<label class=\"form-label\" for=\"password\">")
			// 역 선택은 처음부터 고를 수 있다 (JS 가 꺼져 있어도 등록할 수 있도록)
			.doesNotContain("disabled")
			.doesNotContain("onclick").doesNotContain("<script>");

		// 서버 검증에서 걸린 안내는 JS 가 없어도 화면에 보인다
		mockMvc.perform(newItemRequest("stationName", "横浜"))
			.andExpect(content().string(containsString("<div id=\"serverError\" class=\"form-error\" role=\"alert\">発見した場所(路線・駅)を正しく選択してください。</div>")));
	}

	@Test
	void JS_없이도_비밀번호를_확인하고_수정_삭제할_수_있다() throws Exception {
		long id = createItem();
		MockHttpSession session = new MockHttpSession();

		// 「編集」「削除」는 링크: JS 가 없으면 비밀번호 창을 연 상세 화면
		mockMvc.perform(get("/items/{id}", id).param("verify", "delete").session(session))
			.andExpect(model().attribute("verifyAction", "delete"))
			.andExpect(content().string(containsString("class=\"modal-overlay open\"")));

		// 틀리면 안내와 함께 다시 비밀번호 창. 4자리 숫자가 아닌 값은 틀린 횟수로 세지 않는다
		mockMvc.perform(post("/items/{id}/verify", id).with(csrf()).session(session).param("action", "delete").param("password", "ab"))
			.andExpect(redirectedUrl("/items/" + id + "?verify=delete"))
			.andExpect(flash().attribute("verifyError", "パスワードが間違っています。\nもう一度入力してください。"));
		mockMvc.perform(post("/items/{id}/verify", id).with(csrf()).session(session).param("action", "delete").param("password", "0000"))
			.andExpect(flash().attribute("verifyError", "パスワードが間違っています。\nもう一度入力してください。"));
		mockMvc.perform(post("/items/{id}/verify", id).with(csrf()).session(session).param("action", "delete").param("password", "0000"))
			.andExpect(flash().attribute("verifyError", "パスワードが間違っています。\nもう一度入力してください。\n(あと3回間違えると、5分間入力できなくなります。)"));

		// 맞으면(전각 숫자도) 삭제 확인 창을 연 상세 화면 → 삭제
		mockMvc.perform(post("/items/{id}/verify", id).with(csrf()).session(session).param("action", "delete").param("password", "１２３４"))
			.andExpect(redirectedUrl("/items/" + id + "?confirmDelete"));
		mockMvc.perform(get("/items/{id}", id).param("confirmDelete", "").session(session))
			.andExpect(model().attribute("confirmDelete", true));
		mockMvc.perform(post("/items/{id}/delete", id).with(csrf()).session(session))
			.andExpect(redirectedUrl("/items"));

		// 수정: 맞으면 수정 화면으로
		long other = createItem();
		mockMvc.perform(post("/items/{id}/verify", other).with(csrf()).param("password", PASSWORD))
			.andExpect(redirectedUrl("/items/" + other + "/edit"));
	}

	@Test
	void 잘못된_페이지_번호도_목록을_보여준다() throws Exception {
		createItem();

		mockMvc.perform(get("/items").param("page", "0")).andExpect(status().isOk());
		mockMvc.perform(get("/items").param("page", "-1")).andExpect(status().isOk());
		mockMvc.perform(get("/items").param("page", "9999")).andExpect(status().isOk());
	}

	@Test
	void CSRF_토큰이_없는_POST는_403() throws Exception {
		long id = createItem();

		mockMvc.perform(post("/items/{id}/check-password", id).param("password", PASSWORD))
			.andExpect(status().isForbidden());
	}

	@Test
	void 없는_게시글은_404() throws Exception {
		mockMvc.perform(get("/items/{id}", 999999))
			.andExpect(status().isNotFound());
	}

	// 정상 입력값으로 채운 등록 요청. overrides 는 "항목명", "값" 순서로 넘기면 해당 값만 바꾼다.
	private MockMultipartHttpServletRequestBuilder newItemRequest(String... overrides) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("foundDate", "2026-01-10");
		params.put("lineName", "山手線");
		params.put("stationName", "新宿");
		params.put("categoryName", "財布");
		params.put("itemName", "黄色い財布");
		params.put("contents", "5号車で見つけました。");
		params.put("password", PASSWORD);
		for (int i = 0; i < overrides.length; i += 2) {
			params.put(overrides[i], overrides[i + 1]);
		}

		MockMultipartHttpServletRequestBuilder request = multipart("/items/new");
		request.with(csrf()); // POST 에는 CSRF 토큰 필요
		params.forEach((name, value) -> request.param(name, value));
		return request;
	}

	private long createItem() throws Exception {
		mockMvc.perform(newItemRequest()).andExpect(redirectedUrl("/items"));
		return (Long) latestRow().get("ID");
	}

	private Map<String, Object> latestRow() {
		return jdbcTemplate.queryForMap("SELECT * FROM FOUND_ITEM ORDER BY ID DESC LIMIT 1");
	}

	private int countItems() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM FOUND_ITEM", Integer.class);
	}
}
