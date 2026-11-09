package com.example.jrfind.domain;

// 댓글을 달 수 있는 게시판
public enum Board {
	FOUND("/items", "Found_Item", "FoundItemId"),    // 見つけた 게시판
	LOST("/lost-items", "Lost_Item", "LostItemId"); // 探しています 게시판

	private final String path;
	// 게시글 테이블
	private final String postTable;
	// POST_COMMENT 에서 게시글 ID 를 넣는 컬럼
	private final String commentColumn;

	Board(String path, String postTable, String commentColumn) {
		this.path = path;
		this.postTable = postTable;
		this.commentColumn = commentColumn;
	}

	public String getPostTable() {
		return postTable;
	}

	public String getCommentColumn() {
		return commentColumn;
	}

	// 게시글 상세 화면 주소 (예: /items/3)
	public String postUrl(Long postId) {
		return path + "/" + postId;
	}
}
