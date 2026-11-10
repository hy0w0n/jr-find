package com.example.jrfind.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.Comment;
import com.example.jrfind.domain.CommentForm;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.repository.CommentRepository;

// 댓글 (두 게시판 공통. 회원만 작성, 쓴 본인만 삭제)
@Service
public class CommentService {
	public static final int RECENT_COMMENTS = 50;

	private final CommentRepository repository;

	public CommentService(CommentRepository repository) {
		this.repository = repository;
	}

	// 상세 화면의 댓글. 댓글이 많은 글은 최근 RECENT_COMMENTS 건만 (화면이 너무 길고 무거워지지 않도록. 댓글 300건에 HTML 270KB 였다)
	// all: 「以前のコメントをすべて表示」를 누른 경우, 마이페이지에서 내 댓글을 눌러 온 경우
	public CommentView getComments(Board board, Long postId, boolean all) {
		if (all) {
			List<Comment> comments = repository.findByPost(board, postId);
			return new CommentView(comments, comments.size());
		}
		List<Comment> recent = repository.findRecentByPost(board, postId, RECENT_COMMENTS);
		int total = (recent.size() < RECENT_COMMENTS) ? recent.size() : repository.countByPost(board, postId);
		return new CommentView(recent, total);
	}

	// comments: 화면에 보여 줄 댓글 (오래된 것부터), total: 전체 댓글 수
	public record CommentView(List<Comment> comments, int total) {
		// 보여 주지 않은 이전 댓글 수
		public int hidden() {
			return total - comments.size();
		}
	}

	// 그 게시글의 댓글이 아니거나 삭제됐으면 null
	public Comment getComment(Board board, Long postId, Long commentId) {
		return repository.findById(board, postId, commentId);
	}

	// 새 댓글 번호를 돌려준다
	public Long addComment(Board board, Long postId, CommentForm form, LoginMember loginMember) {
		Comment comment = new Comment();
		comment.setContents(form.getContents().strip());
		comment.setAuthor(loginMember.getNickname());
		comment.setMemberId(loginMember.getId());
		comment.setRegistrationDate(LocalDateTime.now());
		return repository.save(board, postId, comment);
	}

	public void deleteComment(Long commentId) {
		repository.delete(commentId);
	}

	// 회원이 쓴 댓글 (마이페이지)
	public List<Comment> getCommentsByMember(Long memberId, int page, int size) {
		return repository.findByMember(memberId, page, size);
	}

	public int countCommentsByMember(Long memberId) {
		return repository.countByMember(memberId);
	}
}
