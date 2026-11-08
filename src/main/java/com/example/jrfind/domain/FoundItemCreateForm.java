package com.example.jrfind.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 등록 화면 전용 입력값: 공통 항목 + 비밀번호 (수정 화면에서는 비밀번호를 변경할 수 없음)
public class FoundItemCreateForm extends FoundItemForm {
	@NotNull(message = "パスワードを入力してください。")
	@Pattern(regexp = "\\d{4}", message = "パスワードは半角数字4桁で入力してください。")
	private String password;

	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
}
