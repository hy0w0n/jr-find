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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.ImageStorage;
import com.example.jrfind.service.MemberService;

// 사진을 바꾸거나 지우거나 게시글을 삭제하면 이전 이미지 파일도 지운다 (두 게시판)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageCleanupTest {
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	@Autowired
	private ImageStorage imageStorage;

	@Test
	void 見つけた_사진을_바꾸거나_지우면_이전_파일을_지운다() throws Exception {
		LoginMember member = newMember();
		String itemName = marker();
		mockMvc.perform(foundRequest("/items/new", itemName).file(image(PNG)).with(user(member)))
			.andExpect(redirectedUrl("/items"));
		long id = idOf("FOUND_ITEM", itemName);
		String first = imgPath("FOUND_ITEM", id);
		assertThat(fileExists(first)).isTrue();

		// 사진은 그대로 두고 내용만 수정 → 파일도 그대로
		mockMvc.perform(foundRequest("/items/" + id + "/edit", itemName).with(user(member)))
			.andExpect(redirectedUrl("/items/" + id));
		assertThat(imgPath("FOUND_ITEM", id)).isEqualTo(first);
		assertThat(fileExists(first)).isTrue();

		// 다른 사진으로 교체 → 이전 파일 삭제
		mockMvc.perform(foundRequest("/items/" + id + "/edit", itemName).file(image(JPEG)).with(user(member)))
			.andExpect(redirectedUrl("/items/" + id));
		String second = imgPath("FOUND_ITEM", id);
		assertThat(second).isNotEqualTo(first).endsWith(".jpg");
		assertThat(fileExists(first)).isFalse();
		assertThat(fileExists(second)).isTrue();

		// 입력값이 잘못돼 수정되지 않으면 새 사진도 저장하지 않고 지금 사진도 지우지 않는다
		List<String> filesBefore = uploadedFiles();
		mockMvc.perform(foundRequest("/items/" + id + "/edit", " ").file(image(PNG)).with(user(member))) // 물건 이름이 빈 칸
			.andExpect(status().isOk());
		assertThat(imgPath("FOUND_ITEM", id)).isEqualTo(second);
		assertThat(uploadedFiles()).isEqualTo(filesBefore);

		// 사진 삭제 → 파일도 삭제
		mockMvc.perform(foundRequest("/items/" + id + "/edit", itemName).param("isImageDeleted", "true").with(user(member)))
			.andExpect(redirectedUrl("/items/" + id));
		assertThat(imgPath("FOUND_ITEM", id)).isNull();
		assertThat(fileExists(second)).isFalse();
	}

	@Test
	void 見つけた_글을_삭제하면_사진_파일도_지워져_주소로도_볼_수_없다() throws Exception {
		LoginMember member = newMember();
		String itemName = marker();
		mockMvc.perform(foundRequest("/items/new", itemName).file(image(PNG)).with(user(member)));
		long id = idOf("FOUND_ITEM", itemName);
		String image = imgPath("FOUND_ITEM", id);
		mockMvc.perform(get("/images/" + image)).andExpect(status().isOk());

		mockMvc.perform(post("/items/{id}/delete", id).with(user(member)).with(csrf()))
			.andExpect(redirectedUrl("/items"));

		assertThat(fileExists(image)).isFalse();
		assertThat(imgPath("FOUND_ITEM", id)).isNull();
		assertThat(jdbcTemplate.queryForObject("SELECT DEL_FLG FROM FOUND_ITEM WHERE ID = ?", Boolean.class, id)).isTrue();
		mockMvc.perform(get("/images/" + image)).andExpect(status().isNotFound());
	}

	@Test
	void 探しています도_사진을_바꾸거나_지우거나_글을_삭제하면_파일을_지운다() throws Exception {
		LoginMember member = newMember();
		String itemName = marker();
		mockMvc.perform(lostRequest("/lost-items/new", itemName).file(image(PNG)).with(user(member)))
			.andExpect(redirectedUrl("/lost-items"));
		long id = idOf("LOST_ITEM", itemName);
		String first = imgPath("LOST_ITEM", id);
		assertThat(fileExists(first)).isTrue();

		mockMvc.perform(lostRequest("/lost-items/" + id + "/edit", itemName).file(image(JPEG)).with(user(member)))
			.andExpect(redirectedUrl("/lost-items/" + id));
		String second = imgPath("LOST_ITEM", id);
		assertThat(fileExists(first)).isFalse();
		assertThat(fileExists(second)).isTrue();

		mockMvc.perform(lostRequest("/lost-items/" + id + "/edit", itemName).param("isImageDeleted", "true").with(user(member)))
			.andExpect(redirectedUrl("/lost-items/" + id));
		assertThat(imgPath("LOST_ITEM", id)).isNull();
		assertThat(fileExists(second)).isFalse();

		mockMvc.perform(lostRequest("/lost-items/" + id + "/edit", itemName).file(image(PNG)).with(user(member)));
		String third = imgPath("LOST_ITEM", id);
		assertThat(fileExists(third)).isTrue();
		mockMvc.perform(post("/lost-items/{id}/delete", id).with(user(member)).with(csrf()))
			.andExpect(redirectedUrl("/lost-items"));
		assertThat(fileExists(third)).isFalse();
		assertThat(imgPath("LOST_ITEM", id)).isNull();
	}

	// 사진을 지우고(✕) 저장했는데 입력 오류로 화면이 다시 열린 경우, 사진이 되살아난 채로 다시 저장되면 안 된다
	@Test
	void 수정_중_입력_오류가_나도_지운_사진은_지운_상태로_보여주고_고른_사진은_다시_고르도록_안내한다() throws Exception {
		LoginMember member = newMember();
		String foundName = marker();
		String lostName = marker();
		mockMvc.perform(foundRequest("/items/new", foundName).file(image(PNG)).with(user(member)));
		mockMvc.perform(lostRequest("/lost-items/new", lostName).file(image(PNG)).with(user(member)));
		long foundId = idOf("FOUND_ITEM", foundName);
		long lostId = idOf("LOST_ITEM", lostName);
		String foundImage = imgPath("FOUND_ITEM", foundId);
		String lostImage = imgPath("LOST_ITEM", lostId);

		// 見つけた: 사진 삭제 + 다른 사진 선택 + 물건 이름을 비워서 오류. 입력한 값(내용)도 그대로 보여준다
		mockMvc.perform(foundRequest("/items/" + foundId + "/edit", " ").param("contents", "内容を書き直しました。")
				.param("isImageDeleted", "true").file(image(JPEG)).with(user(member)))
			.andExpect(status().isOk())
			.andExpect(model().attribute("isImageDeleted", true))
			.andExpect(model().attribute("errorMessage", containsString("選択した画像は、もう一度選択してください。")))
			.andExpect(content().string(containsString("name=\"isImageDeleted\" value=\"true\"")))
			.andExpect(content().string(containsString("内容を書き直しました。")))
			.andExpect(content().string(not(containsString(foundImage))));

		// 探しています
		mockMvc.perform(lostRequest("/lost-items/" + lostId + "/edit", " ").param("isImageDeleted", "true").with(user(member)))
			.andExpect(status().isOk())
			.andExpect(model().attribute("isImageDeleted", true))
			.andExpect(model().attribute("errorMessage", not(containsString("選択した画像")))) // 새로 고른 사진이 없으면 안내하지 않음
			.andExpect(content().string(containsString("name=\"isImageDeleted\" value=\"true\"")))
			.andExpect(content().string(not(containsString(lostImage))));

		// DB 와 파일은 그대로
		assertThat(imgPath("FOUND_ITEM", foundId)).isEqualTo(foundImage);
		assertThat(imgPath("LOST_ITEM", lostId)).isEqualTo(lostImage);
		assertThat(fileExists(foundImage)).isTrue();
		assertThat(fileExists(lostImage)).isTrue();
	}

	// ---------- 도우미 ----------

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("img_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("写真の人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}

	// 게시글을 찾기 위한 물건 이름
	private String marker() {
		return "写真" + UUID.randomUUID().toString().substring(0, 8);
	}

	private MockMultipartFile image(byte[] header) {
		return new MockMultipartFile("imageFile", "photo", "image/png", header);
	}

	private MockMultipartHttpServletRequestBuilder foundRequest(String url, String itemName) {
		MockMultipartHttpServletRequestBuilder request = multipart(url);
		request.with(csrf());
		request.param("foundDate", "2026-01-10")
			.param("lineName", "山手線")
			.param("stationName", "新宿")
			.param("categoryName", "財布")
			.param("itemName", itemName)
			.param("contents", "5号車で見つけました。");
		return request;
	}

	private MockMultipartHttpServletRequestBuilder lostRequest(String url, String itemName) {
		MockMultipartHttpServletRequestBuilder request = multipart(url);
		request.with(csrf());
		request.param("lostDate", "2026-01-12")
			.param("lineName", "山手線")
			.param("stationName", "渋谷")
			.param("categoryName", "財布")
			.param("itemName", itemName)
			.param("contents", "ハチ公口の改札付近で落としたと思います。")
			.param("warningConfirmed", "true");
		return request;
	}

	private long idOf(String table, String itemName) {
		return jdbcTemplate.queryForObject("SELECT ID FROM " + table + " WHERE ITEMNAME = ?", Long.class, itemName);
	}

	private String imgPath(String table, long id) {
		return jdbcTemplate.queryForObject("SELECT IMGPATH FROM " + table + " WHERE ID = ?", String.class, id);
	}

	private boolean fileExists(String filename) {
		return Files.exists(imageStorage.getUploadDir().resolve(filename));
	}

	private List<String> uploadedFiles() throws IOException {
		try (Stream<Path> files = Files.list(imageStorage.getUploadDir())) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}
}
