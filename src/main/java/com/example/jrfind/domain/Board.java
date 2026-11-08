package com.example.jrfind.domain;

// 댓글을 달 수 있는 게시판
public enum Board {
	FOUND("/items", "FoundItemId"),    // 見つけた 게시판
	LOST("/lost-items", "LostItemId"); // 探しています 게시판

	private final String path;
	// POST_COMMENT 에서 게시글 ID 를 넣는 컬럼
	private final String commentColumn;

	Board(String path, String commentColumn) {
		this.path = path;
		this.commentColumn = commentColumn;
	}

	public String getCommentColumn() {
		return commentColumn;
	}

	// 게시글 상세 화면 주소 (예: /items/3)
	public String postUrl(Long postId) {
		return path + "/" + postId;
	}
}
