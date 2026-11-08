package com.example.jrfind.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// パスワード変更 화면 입력값
// 현재 비밀번호 일치, 새 비밀번호 확인 일치는 컨트롤러에서 확인한다.
public class PasswordForm {
	@NotBlank(message = "現在のパスワードを入力してください。")
	private String currentPassword;

	// 규칙은 회원가입과 같다
	@NotNull(message = "新しいパスワードを入力してください。")
	@Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}", message = "パスワードは英字と数字を含む8〜64文字(半角)で入力してください。")
	private String newPassword;

	private String newPasswordConfirm;

	public String getCurrentPassword() {
		return currentPassword;
	}
	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}
	public String getNewPassword() {
		return newPassword;
	}
	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}
	public String getNewPasswordConfirm() {
		return newPasswordConfirm;
	}
	public void setNewPasswordConfirm(String newPasswordConfirm) {
		this.newPasswordConfirm = newPasswordConfirm;
	}
}
