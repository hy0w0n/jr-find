package com.example.jrfind.domain;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 会員情報変更 화면 입력값 (ID·비밀번호는 여기서 바꾸지 않음). 검사 규칙은 회원가입과 같다.
public class ProfileForm {
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

	// 0 으로 시작하는 10~11자리. 전각 숫자·하이픈은 setPhone 에서 반각으로 바꾼 뒤 검사한다
	@NotBlank(message = "電話番号を入力してください。")
	@Pattern(regexp = "\\s*|(?=(?:-?\\d){10,11}$)0\\d{1,4}-?\\d{1,4}-?\\d{3,4}", message = "電話番号の形式が正しくありません。(例: 090-1234-5678)")
	private String phone;

	// 지금 저장된 회원정보로 채운 폼 (수정 화면 처음 열 때)
	public static ProfileForm from(Member member) {
		ProfileForm form = new ProfileForm();
		form.setRealName(member.getRealName());
		// 닉네임을 비워 두고 가입한 회원은 氏名 이 작성자명으로 저장돼 있다. 닉네임 칸은 비워서 보여준다
		// (채워 두면 氏名 만 바꿨을 때 예전 氏名 이 계속 작성자명으로 남는다)
		form.setNickname(member.getNickname().equals(member.getRealName()) ? "" : member.getNickname());
		form.setBirthDate(member.getBirthDate());
		form.setPhone(member.getPhone());
		return form;
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
		this.phone = Texts.normalizePhone(phone);
	}
}
