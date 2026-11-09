package com.example.jrfind.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.jrfind.config.ExpiredFormHandler;
import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.Comment;
import com.example.jrfind.domain.CommentForm;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.LostItemService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

// 댓글 작성·삭제 (見つけた / 探しています 게시판 공통)
// 댓글 목록은 각 게시판의 상세 화면에서 보여준다.
// 여기 있는 주소는 모두 로그인이 필요하다 (SecurityConfig). 삭제는 댓글을 쓴 본인만.
@Controller
public class CommentController {
	private final CommentService commentService;
	// 게시글이 있는지 확인용
	private final JrfindService foundService;
	private final LostItemService lostService;

	public CommentController(CommentService commentService, JrfindService foundService, LostItemService lostService) {
		this.commentService = commentService;
		this.foundService = foundService;
		this.lostService = lostService;
	}

	// ---------- 見つけた 게시판 ----------

	// 로그인하지 않은 사람이 댓글 영역의 「ログイン」을 누르면 이 주소로 온다.
	// 로그인 화면을 거쳐 다시 여기로 돌아오면 상세 화면의 댓글 위치로 보낸다.
	@GetMapping("/items/{id}/comments")
	public String foundComments(@PathVariable("id") Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
		requirePost(Board.FOUND, id);
		return backToCommentArea(Board.FOUND, id, request, redirectAttributes);
	}

	@PostMapping("/items/{id}/comments")
	public String addFoundComment(
			@PathVariable("id") Long id,
			@Valid @ModelAttribute CommentForm form,
			BindingResult bindingResult,
			@AuthenticationPrincipal LoginMember loginMember,
			RedirectAttributes redirectAttributes) {
		return addComment(Board.FOUND, id, form, bindingResult, loginMember, redirectAttributes);
	}

	@PostMapping("/items/{id}/comments/{commentId}/delete")
	public String deleteFoundComment(
			@PathVariable("id") Long id,
			@PathVariable("commentId") Long commentId,
			@AuthenticationPrincipal LoginMember loginMember) {
		return deleteComment(Board.FOUND, id, commentId, loginMember);
	}

	// ---------- 探しています 게시판 ----------

	@GetMapping("/lost-items/{id}/comments")
	public String lostComments(@PathVariable("id") Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
		requirePost(Board.LOST, id);
		return backToCommentArea(Board.LOST, id, request, redirectAttributes);
	}

	@PostMapping("/lost-items/{id}/comments")
	public String addLostComment(
			@PathVariable("id") Long id,
			@Valid @ModelAttribute CommentForm form,
			BindingResult bindingResult,
			@AuthenticationPrincipal LoginMember loginMember,
			RedirectAttributes redirectAttributes) {
		return addComment(Board.LOST, id, form, bindingResult, loginMember, redirectAttributes);
	}

	@PostMapping("/lost-items/{id}/comments/{commentId}/delete")
	public String deleteLostComment(
			@PathVariable("id") Long id,
			@PathVariable("commentId") Long commentId,
			@AuthenticationPrincipal LoginMember loginMember) {
		return deleteComment(Board.LOST, id, commentId, loginMember);
	}

	// ---------- 공통 ----------

	private String addComment(Board board, Long postId, CommentForm form, BindingResult bindingResult,
			LoginMember loginMember, RedirectAttributes redirectAttributes) {
		requirePost(board, postId);
		if (bindingResult.hasErrors()) {
			// 상세 화면의 댓글 입력 칸으로 돌아가서 오류 메시지를 보여주고, 입력한 내용은 지우지 않는다
			// (댓글이 많아도 오류 메시지가 화면에 보이도록 입력 칸 위치로)
			redirectAttributes.addFlashAttribute("commentError", FormErrors.firstMessage(bindingResult));
			redirectAttributes.addFlashAttribute("commentDraft", form.getContents());
			return "redirect:" + board.postUrl(postId) + "#commentForm";
		}
		// 등록한 댓글 위치로 (댓글 영역 맨 위로 가면 댓글이 많을 때 새 댓글이 화면 밖에 있다)
		Long commentId = commentService.addComment(board, postId, form, loginMember);
		return "redirect:" + board.postUrl(postId) + "#comment-" + commentId;
	}

	private String deleteComment(Board board, Long postId, Long commentId, LoginMember loginMember) {
		requirePost(board, postId);
		Comment comment = commentService.getComment(board, postId, commentId);
		if (comment == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		// 댓글을 쓴 본인이 아니면 403
		if (!comment.getMemberId().equals(loginMember.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		commentService.deleteComment(commentId);
		return backToPost(board, postId);
	}

	// 로그인 링크로 돌아왔을 때: 로그인이 끝나서 보내지 못한 댓글(ExpiredFormHandler)이 있으면 입력 칸에 다시 채운다
	private String backToCommentArea(Board board, Long postId, HttpServletRequest request, RedirectAttributes redirectAttributes) {
		HttpSession session = request.getSession(false);
		String attribute = ExpiredFormHandler.pendingDraftAttribute(request.getRequestURI());
		if (session != null && session.getAttribute(attribute) instanceof String draft) {
			session.removeAttribute(attribute);
			redirectAttributes.addFlashAttribute("commentDraft", draft);
			return "redirect:" + board.postUrl(postId) + "#commentForm";
		}
		return backToPost(board, postId);
	}

	// 상세 화면의 댓글 위치로
	private String backToPost(Board board, Long postId) {
		return "redirect:" + board.postUrl(postId) + "#comments";
	}

	// 없는 글·삭제된 글이면 404
	private void requirePost(Board board, Long postId) {
		boolean exists = switch (board) {
			case FOUND -> foundService.getItem(postId) != null;
			case LOST -> lostService.getItem(postId) != null;
		};
		if (!exists) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}
}
