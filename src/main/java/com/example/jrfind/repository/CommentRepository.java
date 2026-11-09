package com.example.jrfind.repository;

import java.util.List;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.Comment;

public interface CommentRepository {
	Long save(Board board, Long postId, Comment comment); // 새 댓글 번호. 게시글이 없거나 삭제됐으면 저장하지 않고 null

	// 오래된 댓글부터
	List<Comment> findByPost(Board board, Long postId);

	// 그 게시글의 댓글이 아니거나 삭제됐으면 null
	Comment findById(Board board, Long postId, Long id);

	void delete(Long id);

	// 회원이 쓴 댓글 (마이페이지, 최신 순). 삭제된 게시글의 댓글은 제외
	List<Comment> findByMember(Long memberId, int page, int size);

	int countByMember(Long memberId);
}
