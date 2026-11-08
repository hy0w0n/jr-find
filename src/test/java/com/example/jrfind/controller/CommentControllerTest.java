package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

// 댓글: 두 게시판 공통, 회원만 작성, 쓴 본인만 삭제
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MemberService memberService;

	// ---------- 로그인 필요 ----------

	@Test
	void 댓글은_로그인_없이_볼_수_있지만_쓰려면_로그인이_필요하다() throws Exception {
		long postId = createFoundPost(null);
		LoginMember member = newMember();
		mockMvc.perform(commentRequest("/items/{id}/comments", postId, "ありがとうございます").with(user(member)));

		mockMvc.perform(get("/items/{id}", postId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("ありがとうございます")))
			.andExpect(content().string(containsString("コメントを書くにはログインが必要です。")))
			.andExpect(content().string(not(containsString("id=\"commentForm\""))));

		// 로그인 없이 보내면 로그인 화면으로, 저장되지 않음
		int before = countComments();
		mockMvc.perform(commentRequest("/items/{id}/comments", postId, "匿名のコメント"))
			.andExpect(redirectedUrl("/login?required"));
		assertThat(countComments()).isEqualTo(before);
	}

	@Test
	void 로그인_링크는_로그인_후_게시글의_댓글_위치로_돌아온다() throws Exception {
		long foundId = createFoundPost(null);
		long lostId = createLostPost(newMember());

		mockMvc.perform(get("/items/{id}/comments", foundId))
			.andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(get("/items/{id}/comments", foundId).with(user(newMember())))
			.andExpect(redirectedUrl("/items/" + foundId + "#comments"));
		mockMvc.perform(get("/lost-items/{id}/comments", lostId).with(user(newMember())))
			.andExpect(redirectedUrl("/lost-items/" + lostId + "#comments"));
	}

	// ---------- 작성 ----------

	@Test
	void 회원은_두_게시판_모두에_닉네임으로_댓글을_쓴다() throws Exception {
		LoginMember member = newMember();
		long foundId = createFoundPost(null);
		long lostId = createLostPost(newMember());

		mockMvc.perform(commentRequest("/items/{id}/comments", foundId, "  新宿駅の忘れ物取扱所に届けました。  ").with(user(member)))
			.andExpect(redirectedUrl("/items/" + foundId + "#comments"));
		Map<String, Object> row = latestComment();
		assertThat(row.get("FOUNDITEMID")).isEqualTo(foundId);
		assertThat(row.get("LOSTITEMID")).isNull();
		assertThat(row.get("CONTENTS")).isEqualTo("新宿駅の忘れ物取扱所に届けました。"); // 앞뒤 공백 제거
		assertThat(row.get("AUTHOR")).isEqualTo(member.getNickname());
		assertThat(row.get("MEMBERID")).isEqualTo(member.getId());

		mockMvc.perform(commentRequest("/lost-items/{id}/comments", lostId, "<script>alert(1)</script>").with(user(member)))
			.andExpect(redirectedUrl("/lost-items/" + lostId + "#comments"));
		row = latestComment();
		assertThat(row.get("LOSTITEMID")).isEqualTo(lostId);
		assertThat(row.get("FOUNDITEMID")).isNull();

		// 각 게시글에는 그 게시글의 댓글만 보이고, HTML 은 이스케이프된다
		mockMvc.perform(get("/lost-items/{id}", lostId))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
			.andExpect(content().string(not(containsString("新宿駅の忘れ物取扱所に届けました。"))));
	}

	@Test
	void 빈_댓글이나_500자를_넘는_댓글은_저장하지_않고_입력값을_돌려준다() throws Exception {
		LoginMember member = newMember();
		long postId = createLostPost(member);
		int before = countComments();

		mockMvc.perform(commentRequest("/lost-items/{id}/comments", postId, "   ").with(user(member)))
			.andExpect(redirectedUrl("/lost-items/" + postId + "#comments"))
			.andExpect(flash().attribute("commentError", "コメントを入力してください。"));

		String tooLong = "あ".repeat(501);
		mockMvc.perform(commentRequest("/lost-items/{id}/comments", postId, tooLong).with(user(member)))
			.andExpect(flash().attribute("commentError", "コメントは500文字以内で入力してください。"))
			.andExpect(flash().attribute("commentDraft", tooLong));

		assertThat(countComments()).isEqualTo(before);

		// 500자는 저장된다
		mockMvc.perform(commentRequest("/lost-items/{id}/comments", postId, "あ".repeat(500)).with(user(member)));
		assertThat(countComments()).isEqualTo(before + 1);
	}

	@Test
	void 없는_게시글이나_삭제된_게시글에는_댓글을_쓸_수_없다() throws Exception {
		LoginMember member = newMember();
		long postId = createFoundPost(null);
		jdbcTemplate.update("UPDATE FOUND_ITEM SET DEL_FLG = TRUE WHERE ID = ?", postId);
		int before = countComments();

		mockMvc.perform(commentRequest("/items/{id}/comments", postId, "コメント").with(user(member)))
			.andExpect(status().isNotFound());
		mockMvc.perform(commentRequest("/lost-items/{id}/comments", 999999L, "コメント").with(user(member)))
			.andExpect(status().isNotFound());

		assertThat(countComments()).isEqualTo(before);
	}

	@Test
	void 게시글_작성자의_댓글에는_投稿者_표시가_붙는다() throws Exception {
		LoginMember owner = newMember();
		LoginMember other = newMember();
		long postId = createLostPost(owner);

		mockMvc.perform(commentRequest("/lost-items/{id}/comments", postId, "見かけた方がいれば教えてください").with(user(owner)));
		mockMvc.perform(commentRequest("/lost-items/{id}/comments", postId, "渋谷駅で見かけました").with(user(other)));

		String html = mockMvc.perform(get("/lost-items/{id}", postId))
			.andReturn().getResponse().getContentAsString();
		assertThat(html.split("class=\"comment-badge\"", -1)).hasSize(2); // 표시는 1개
		assertThat(html.indexOf("comment-badge")).isLessThan(html.indexOf("渋谷駅で見かけました")); // 먼저 쓴 작성자 댓글 쪽
	}

	// ---------- 삭제 ----------

	@Test
	void 댓글은_쓴_본인만_삭제할_수_있다() throws Exception {
		LoginMember writer = newMember();
		LoginMember other = newMember();
		long postId = createFoundPost(null);
		mockMvc.perform(commentRequest("/items/{id}/comments", postId, "消される予定のコメント").with(user(writer)));
		long commentId = (Long) latestComment().get("ID");
		String deleteUrl = "/items/" + postId + "/comments/" + commentId + "/delete";

		// 삭제 버튼은 쓴 본인에게만
		mockMvc.perform(get("/items/{id}", postId).with(user(writer)))
			.andExpect(content().string(containsString(deleteUrl)));
		mockMvc.perform(get("/items/{id}", postId).with(user(other)))
			.andExpect(content().string(not(containsString(deleteUrl))));

		mockMvc.perform(post(deleteUrl).with(csrf()))
			.andExpect(redirectedUrl("/login?required"));
		mockMvc.perform(post(deleteUrl).with(user(other)).with(csrf()))
			.andExpect(status().isForbidden());
		assertThat(findComment(commentId).get("DEL_FLG")).isEqualTo(false);

		mockMvc.perform(post(deleteUrl).with(user(writer)).with(csrf()))
			.andExpect(redirectedUrl("/items/" + postId + "#comments"));
		assertThat(findComment(commentId).get("DEL_FLG")).isEqualTo(true);
		mockMvc.perform(get("/items/{id}", postId))
			.andExpect(content().string(not(containsString("消される予定のコメント"))));

		// 이미 삭제한 댓글은 404
		mockMvc.perform(post(deleteUrl).with(user(writer)).with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 다른_게시글의_주소로는_댓글을_삭제할_수_없다() throws Exception {
		LoginMember writer = newMember();
		long postId = createFoundPost(null);
		long otherFoundId = createFoundPost(null);
		long lostId = createLostPost(writer);
		mockMvc.perform(commentRequest("/items/{id}/comments", postId, "コメント").with(user(writer)));
		long commentId = (Long) latestComment().get("ID");

		mockMvc.perform(post("/items/{id}/comments/{cid}/delete", otherFoundId, commentId).with(user(writer)).with(csrf()))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/lost-items/{id}/comments/{cid}/delete", lostId, commentId).with(user(writer)).with(csrf()))
			.andExpect(status().isNotFound());
		assertThat(findComment(commentId).get("DEL_FLG")).isEqualTo(false);
	}

	// ---------- 목록 ----------

	@Test
	@SuppressWarnings("unchecked")
	void 목록에는_삭제되지_않은_댓글_수가_표시된다() throws Exception {
		LoginMember member = newMember();
		long foundId = createFoundPost(null);
		long lostId = createLostPost(member);
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(commentRequest("/items/{id}/comments", foundId, "コメント" + i).with(user(member)));
		}
		mockMvc.perform(commentRequest("/lost-items/{id}/comments", lostId, "コメント").with(user(member)));
		mockMvc.perform(post("/items/{id}/comments/{cid}/delete", foundId, latestCommentId("FOUNDITEMID", foundId))
			.with(user(member)).with(csrf()));

		List<FoundItem> foundItems = (List<FoundItem>) mockMvc.perform(get("/items"))
			.andExpect(content().string(containsString("[2]")))
			.andReturn().getModelAndView().getModel().get("items");
		assertThat(foundItems).filteredOn(item -> item.getId() == foundId)
			.singleElement().extracting(FoundItem::getCommentCount).isEqualTo(2);

		List<LostItem> lostItems = (List<LostItem>) mockMvc.perform(get("/lost-items"))
			.andReturn().getModelAndView().getModel().get("items");
		assertThat(lostItems).filteredOn(item -> item.getId() == lostId)
			.singleElement().extracting(LostItem::getCommentCount).isEqualTo(1);
	}

	// ---------- 도우미 ----------

	private LoginMember newMember() {
		SignupForm form = new SignupForm();
		form.setLoginId("cmt_" + UUID.randomUUID().toString().substring(0, 8));
		form.setPassword("secret123");
		form.setPasswordConfirm("secret123");
		form.setRealName("田中花子");
		form.setNickname("コメント人");
		form.setBirthDate(LocalDate.of(1999, 9, 9));
		form.setPhone("090-1111-2222");
		form.setAgree(true);
		memberService.register(form);
		return (LoginMember) memberService.loadUserByUsername(form.getLoginId());
	}

	private MockHttpServletRequestBuilder commentRequest(String url, long postId, String contents) {
		return post(url, postId).with(csrf()).param("contents", contents);
	}

	// 見つけた 게시글 (memberId 가 null 이면 비회원 글)
	private long createFoundPost(Long memberId) {
		jdbcTemplate.update("INSERT INTO FOUND_ITEM (TITLE, FOUNDDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-08][山手線][新宿][スマホ・携帯電話] iPhone', '2026-01-08', '山手線', '新宿', 'スマホ・携帯電話', 'iPhone', '2番線ホームで拾いました。', '匿名の天使001', ?, ?)",
			memberId, LocalDateTime.now());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM FOUND_ITEM", Long.class);
	}

	private long createLostPost(LoginMember owner) {
		jdbcTemplate.update("INSERT INTO LOST_ITEM (TITLE, LOSTDATE, LINENAME, STATIONNAME, CATEGORYNAME, ITEMNAME, CONTENTS, AUTHOR, MEMBERID, REGISTRATIONDATE)"
				+ " VALUES ('[2026-01-12][山手線][渋谷][財布] 黒い長財布', '2026-01-12', '山手線', '渋谷', '財布', '黒い長財布', 'ハチ公口付近です。', ?, ?, ?)",
			owner.getNickname(), owner.getId(), LocalDateTime.now());
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM LOST_ITEM", Long.class);
	}

	private Map<String, Object> latestComment() {
		return jdbcTemplate.queryForMap("SELECT * FROM POST_COMMENT ORDER BY ID DESC LIMIT 1");
	}

	private long latestCommentId(String column, long postId) {
		return jdbcTemplate.queryForObject("SELECT max(ID) FROM POST_COMMENT WHERE " + column + " = ?", Long.class, postId);
	}

	private Map<String, Object> findComment(long id) {
		return jdbcTemplate.queryForMap("SELECT * FROM POST_COMMENT WHERE ID = ?", id);
	}

	private int countComments() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM POST_COMMENT", Integer.class);
	}
}
