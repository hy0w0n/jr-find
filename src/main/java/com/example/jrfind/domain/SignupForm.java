package com.example.jrfind.domain;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 화면 입력값
// 비밀번호 확인 일치, ID 중복 등 DB나 다른 항목이 필요한 검사는 컨트롤러에서 한다.
public class SignupForm {
	@NotNull(message = "IDを入力してください。")
	@Pattern(regexp = "[A-Za-z0-9_]{4,20}", message = "IDは半角英数字と _ で4〜20文字で入力してください。")
	private String loginId;

	@NotNull(message = "パスワードを入力してください。")
	@Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}", message = "パスワードは英字と数字を含む8〜64文字(半角)で入力してください。")
	private String password;

	private String passwordConfirm;

	@NotBlank(message = "氏名を入力してください。")
	@Size(max = 20, message = "氏名は20文字以内で入力してください。")
	private String realName;

	// 비워 두면 氏名 을 작성자명으로 사용
	@Size(max = 20, message = "ニックネームは20文字以内で入力してください。")
	private String nickname;

	@NotNull(message = "生年月日を入力してください。")
	@Past(message = "生年月日を正しく入力してください。")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate birthDate;

	@NotNull(message = "電話番号を入力してください。")
	@Pattern(regexp = "[0-9]{2,4}-?[0-9]{2,4}-?[0-9]{3,4}", message = "電話番号の形式が正しくありません。(例: 090-1234-5678)")
	private String phone;

	@AssertTrue(message = "個人情報の取り扱いに同意してください。")
	private boolean agree;

	public String getLoginId() {
		return loginId;
	}
	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getPasswordConfirm() {
		return passwordConfirm;
	}
	public void setPasswordConfirm(String passwordConfirm) {
		this.passwordConfirm = passwordConfirm;
	}
	public String getRealName() {
		return realName;
	}
	// 앞뒤의 전각 공백·폭 없는 글자도 지운다 (공백만 입력하면 빈 칸)
	public void setRealName(String realName) {
		this.realName = Texts.strip(realName);
	}
	public String getNickname() {
		return nickname;
	}
	public void setNickname(String nickname) {
		this.nickname = Texts.strip(nickname);
	}
	public LocalDate getBirthDate() {
		return birthDate;
	}
	public void setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}
	public String getPhone() {
		return phone;
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public boolean isAgree() {
		return agree;
	}
	public void setAgree(boolean agree) {
		this.agree = agree;
	}
}
