package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.CommentForm;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.repository.JrfindRepository;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.ImageStorage;
import com.example.jrfind.service.MemberService;

// 같은 게시글을 동시에 수정·삭제하거나, 글을 지우는 순간에 댓글을 쓴 경우
// 실제로 동시에 보내면 결과가 그때그때 달라지므로, 요청 처리 도중(사진 저장·삭제 직전)에 다른 요청이 끼어든 상황을 만들어 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentEditTest {
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	@Autowired
	private CommentService commentService;

	@MockitoSpyBean
	private ImageStorage imageStorage;

	@MockitoSpyBean
	private JrfindRepository repository;

	@AfterEach
	void restore() {
		reset(imageStorage, repository);
	}

	@Test
	void 사진을_바꾸는_중에_글이_삭제되면_반영하지_않고_새_사진_파일도_지운다() throws Exception {
		LoginMember member = newMember();
		long id = createPostWithImage(member);
		List<String> before = uploadedFiles();
		// 새 사진을 저장하는 사이에 다른 탭에서 글을 삭제
		doAnswer(invocation -> {
			jdbcTemplate.update("UPDATE FOUND_ITEM SET DEL_FLG = TRUE WHERE ID = ?", id);
			return invocation.callRealMethod();
		}).when(imageStorage).store(any());

		mockMvc.perform(editRequest(id).file(new MockMultipartFile("imageFile", "new.png", "image/png", PNG)).with(user(member)))
			.andExpect(model().attribute("errorMessage", "この投稿は削除されています。\n(選択した画像は、もう一度選択してください。)"));
		assertThat(uploadedFiles()).isEqualTo(before); // 새 사진 파일이 남지 않음
	}

	@Test
	void 사진을_바꾸는_중에_다른_요청이_사진을_바꾸면_반영하지_않고_새_사진_파일도_지운다() throws Exception {
		LoginMember member = newMember();
		long id = createPostWithImage(member);
		List<String> before = uploadedFiles();
		doAnswer(invocation -> {
			jdbcTemplate.update("UPDATE FOUND_ITEM SET IMGPATH = 'other-request.png' WHERE ID = ?", id);
			return invocation.callRealMethod();
		}).when(imageStorage).store(any());

		mockMvc.perform(editRequest(id).file(new MockMultipartFile("imageFile", "new.png", "image/png", PNG)).with(user(member)))
			.andExpect(model().attribute("errorMessage",
				"ほかの画面で同時に変更されたため、保存できませんでした。もう一度お試しください。\n(選択した画像は、もう一度選択してください。)"));
		assertThat(uploadedFiles()).isEqualTo(before);
		assertThat(jdbcTemplate.queryForObject("SELECT IMGPATH FROM FOUND_ITEM WHERE ID = ?", String.class, id)).isEqualTo("other-request.png");
	}

	@Test
	void 삭제하는_중에_다른_요청이_사진을_바꾸면_바뀐_사진_파일을_지운다() throws Exception {
		LoginMember member = newMember();
		long id = createPostWithImage(member);
		String first = imgPath(id);
		// 삭제가 글을 읽은 뒤, 반영하기 전에 다른 요청이 사진을 바꿈 (첫 번째 삭제 시도에서만)
		Path second = imageStorage.getUploadDir().resolve("changed-" + UUID.randomUUID() + ".png");
		Files.write(second, PNG);
		AtomicBoolean firstAttempt = new AtomicBoolean(true);
		doAnswer(invocation -> {
			if (firstAttempt.getAndSet(false)) {
				jdbcTemplate.update("UPDATE FOUND_ITEM SET IMGPATH = ? WHERE ID = ?", second.getFileName().toString(), id);
				Files.delete(imageStorage.getUploadDir().resolve(first)); // 바꾼 요청이 이전 파일을 지운다
			}
			return invocation.callRealMethod();
		}).when(repository).delete(anyLong(), anyString());

		mockMvc.perform(post("/items/{id}/delete", id).with(csrf()).with(user(member)))
			.andExpect(redirectedUrl("/items"));
		assertThat(Files.exists(second)).isFalse(); // 바뀐 사진도 지웠다
		assertThat(jdbcTemplate.queryForObject("SELECT DEL_FLG FROM FOUND_ITEM WHERE ID = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void 글을_지운_뒤에_도착한_댓글은_저장하지_않는다() {
		LoginMember member = newMember();
		long id = createPostWithoutImage(member);
		// 댓글 화면에서 글이 있는지 확인한 뒤, 저장하기 전에 글이 삭제된 경우
		jdbcTemplate.update("UPDATE FOUND_ITEM SET DEL_FLG = TRUE WHERE ID = ?", id);
		CommentForm form = new CommentForm();
		form.setContents("遅れて届いたコメント");

		assertThat(commentService.addComment(Board.FOUND, id, form, member)).isNull();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM POST_COMMENT WHERE FOUNDITEMID = ?", Integer.class, id)).isZero();
	}

	@Test
	void 匿名の天使_번호는_삭제된_글의_번호를_다시_쓰고_1000개가_다_차도_등록할_수_있다() throws Exception {
		// 삭제된 글의 작성자명은 사용 중으로 세지 않는다
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, DEL_FLG, REGISTRATIONDATE)"
				+ " VALUES ('t', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', '匿名の天使 4321', TRUE, CURRENT_TIMESTAMP)");
		assertThat(repository.findAllAuthors()).doesNotContain("匿名の天使 4321");

		// 1000개가 모두 쓰이는 중이어도 등록된다 (예전에는 이후 비회원 글이 전부 가장 오래된 글과 같은 이름이 됐다)
		doReturn(IntStream.range(0, 1000).mapToObj(n -> String.format("匿名の天使 %03d", n)).toList()).when(repository).findAllAuthors();
		String itemName = "番号" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(foundRequest("/items/new", itemName).param("password", "1234"))
			.andExpect(redirectedUrl("/items"));
		assertThat(jdbcTemplate.queryForObject("SELECT AUTHOR FROM FOUND_ITEM WHERE ITEMNAME = ?", String.class, itemName))
			.matches("匿名の天使 \\d{3}");
	}

	// ---------- 도우미 ----------

	private long createPostWithImage(LoginMember member) throws Exception {
		String itemName = "同時" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(foundRequest("/items/new", itemName).file(new MockMultipartFile("imageFile", "first.png", "image/png", PNG)).with(user(member)))
			.andExpect(redirectedUrl("/items"));
		return jdbcTemplate.queryForObject("SELECT ID FROM FOUND_ITEM WHERE ITEMNAME = ?", Long.class, itemName);
	}

	private long createPostWithoutImage(LoginMember member) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('t', '2026-01-08', '山手線', '新宿', 'その他', '傘', '本文', ?, ?, CURRENT_TIMESTAMP)", member.getNickname(), member.getId());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private MockMultipartHttpServletRequestBuilder editRequest(long id) {
		return foundRequest("/items/" + id + "/edit", "書き換えた財布");
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

	private String imgPath(long id) {
		return jdbcTemplate.queryForObject("SELECT IMGPATH FROM FOUND_ITEM WHERE ID = ?", String.class, id);
	}

	private List<String> uploadedFiles() throws IOException {
		try (Stream<Path> files = Files.list(imageStorage.getUploadDir())) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("con_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("山田太郎");
		form.setNickname("同時の人");
		form.setBirthDate(LocalDate.of(2000, 4, 16));
		form.setPhone("090-1234-5678");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}
}
