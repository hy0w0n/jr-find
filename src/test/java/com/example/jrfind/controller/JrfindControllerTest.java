package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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

		mockMvc.perform(post("/items/{id}/delete", id))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/items/{id}", id))
			.andExpect(status().isOk());
	}

	@Test
	void 비밀번호를_틀리면_삭제할_수_없다() throws Exception {
		long id = createItem();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/items/{id}/check-password", id).session(session).param("password", "0000"))
			.andExpect(content().string("false"));
		mockMvc.perform(post("/items/{id}/delete", id).session(session))
			.andExpect(status().isForbidden());
	}

	@Test
	void 비밀번호_확인_후_삭제하면_이후_상세_화면은_404() throws Exception {
		long id = createItem();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/items/{id}/check-password", id).session(session).param("password", PASSWORD))
			.andExpect(content().string("true"));
		mockMvc.perform(post("/items/{id}/delete", id).session(session))
			.andExpect(redirectedUrl("/items"));

		mockMvc.perform(get("/items/{id}", id))
			.andExpect(status().isNotFound());
	}

	@Test
	void 수정_화면은_비밀번호_확인이_필요하고_비밀번호를_HTML에_내보내지_않는다() throws Exception {
		long id = createItem();
		String hash = (String) latestRow().get("PASSWORD");

		// 확인 없이 URL로 직접 접근 → 상세 화면으로
		mockMvc.perform(get("/items/{id}/edit", id))
			.andExpect(redirectedUrl("/items/" + id));

		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/items/{id}/check-password", id).session(session).param("password", PASSWORD));
		mockMvc.perform(get("/items/{id}/edit", id).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(hash))))
			.andExpect(content().string(not(containsString("name=\"password\""))));
	}

	@Test
	void 비밀번호_확인_없이_수정_요청하면_403() throws Exception {
		long id = createItem();

		mockMvc.perform(multipart("/items/{id}/edit", id)
				.param("foundDate", "2026-01-10")
				.param("lineName", "山手線")
				.param("stationName", "新宿")
				.param("categoryName", "財布")
				.param("itemName", "書き換え")
				.param("contents", "書き換え"))
			.andExpect(status().isForbidden());
	}

	@Test
	void 비밀번호를_연속으로_틀리면_잠긴다() throws Exception {
		long id = createItem();

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/items/{id}/check-password", id).param("password", "0000"))
				.andExpect(content().string("false"));
		}
		// 잠긴 동안에는 맞는 비밀번호도 거부
		mockMvc.perform(post("/items/{id}/check-password", id).param("password", PASSWORD))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void 이미지가_아닌_파일은_저장하지_않는다() throws Exception {
		int before = countItems();
		MockMultipartFile html = new MockMultipartFile("imageFile", "evil.png", "image/png", "<html><script>alert(1)</script></html>".getBytes());

		mockMvc.perform(newItemRequest().file(html))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errorMessage", "画像ファイル(JPG・PNG・GIF・WEBP)のみアップロードできます。"));

		assertThat(countItems()).isEqualTo(before);
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
	void 잘못된_페이지_번호도_목록을_보여준다() throws Exception {
		createItem();

		mockMvc.perform(get("/items").param("page", "0")).andExpect(status().isOk());
		mockMvc.perform(get("/items").param("page", "-1")).andExpect(status().isOk());
		mockMvc.perform(get("/items").param("page", "9999")).andExpect(status().isOk());
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
