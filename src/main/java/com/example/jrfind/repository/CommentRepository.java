package com.example.jrfind.repository;

import java.util.List;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.Comment;

public interface CommentRepository {
	void save(Board board, Long postId, Comment comment);

	// 오래된 댓글부터
	List<Comment> findByPost(Board board, Long postId);

	// 그 게시글의 댓글이 아니거나 삭제됐으면 null
	Comment findById(Board board, Long postId, Long id);

	void delete(Long id);
}
