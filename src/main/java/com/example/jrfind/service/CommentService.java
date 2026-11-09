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
	private final CommentRepository repository;

	public CommentService(CommentRepository repository) {
		this.repository = repository;
	}

	public List<Comment> getComments(Board board, Long postId) {
		return repository.findByPost(board, postId);
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
