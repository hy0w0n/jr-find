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
	// 줄바꿈은 LF 로 맞추고(화면의 글자 수와 같게), 앞뒤 공백·폭 없는 글자는 지운다 (보이지 않는 글자만 있으면 빈 댓글)
	public void setContents(String contents) {
		this.contents = Texts.strip(Texts.normalizeNewlines(contents));
	}
}
