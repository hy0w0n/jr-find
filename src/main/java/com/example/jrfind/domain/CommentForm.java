package com.example.jrfind.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 댓글 입력값
public class CommentForm {
	@NotBlank(message = "コメントを入力してください。")
	@Size(max = 500, message = "コメントは500文字以内で入力してください。")
	private String contents;

	public String getContents() {
		return contents;
	}
	public void setContents(String contents) {
		this.contents = contents;
	}
}
