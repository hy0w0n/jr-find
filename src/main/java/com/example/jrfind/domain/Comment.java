package com.example.jrfind.domain;

import java.time.LocalDateTime;

// 게시글 댓글 (회원만 작성)
public class Comment {
	private Long id;
	private String contents;
	private String author; // 작성 시점의 닉네임
	private Long memberId;
	private LocalDateTime registrationDate;
	// 마이페이지에서만 채움: 댓글이 달린 게시글
	private Board board;
	private Long postId;
	private String postTitle;
	private String postItemName; // 게시글의 물건 이름 (마이페이지)

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getContents() {
		return contents;
	}
	public void setContents(String contents) {
		this.contents = contents;
	}
	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}
	public Long getMemberId() {
		return memberId;
	}
	public void setMemberId(Long memberId) {
		this.memberId = memberId;
	}
	public LocalDateTime getRegistrationDate() {
		return registrationDate;
	}
	public void setRegistrationDate(LocalDateTime registrationDate) {
		this.registrationDate = registrationDate;
	}
	public Board getBoard() {
		return board;
	}
	public void setBoard(Board board) {
		this.board = board;
	}
	public Long getPostId() {
		return postId;
	}
	public void setPostId(Long postId) {
		this.postId = postId;
	}
	public String getPostTitle() {
		return postTitle;
	}
	public void setPostTitle(String postTitle) {
		this.postTitle = postTitle;
	}
	public String getPostItemName() {
		return postItemName;
	}
	public void setPostItemName(String postItemName) {
		this.postItemName = postItemName;
	}

	// 댓글이 달린 게시글의 댓글 위치 (마이페이지 링크)
	// 오래된 댓글은 상세 화면에 처음부터 보이지 않으므로(최근 것만), 모든 댓글을 보여 주는 주소로
	public String getPostUrl() {
		return board.postUrl(postId) + "?allComments#comment-" + id;
	}
}
