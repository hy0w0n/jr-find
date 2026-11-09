package com.example.jrfind.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

// 退会 화면 입력값
// 비밀번호 일치는 컨트롤러에서 확인한다.
public class WithdrawForm {
	@NotBlank(message = "現在のパスワードを入力してください。") // 화면의 항목 이름 「現在のパスワード」와 맞춘다
	private String currentPassword;

	// 注意事項(회원정보 삭제, 글·댓글은 「退会したユーザー」로 남음)을 확인했는지
	@AssertTrue(message = "注意事項を確認し、チェックを入れてください。")
	private boolean agree;

	public String getCurrentPassword() {
		return currentPassword;
	}
	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}
	public boolean isAgree() {
		return agree;
	}
	public void setAgree(boolean agree) {
		this.agree = agree;
	}
}
