package com.example.jrfind.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 회원
public class Member {
	private Long id;
	private String loginId;
	private String password; // BCrypt 해시
	private String realName;
	private String nickname;
	private LocalDate birthDate;
	private String phone;
	private LocalDateTime agreedDate; // 개인정보 취급 동의 일시
	private LocalDateTime registrationDate;
	private LocalDateTime withdrawnDate; // 탈퇴 일시 (탈퇴하지 않았으면 null)

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
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
	public String getRealName() {
		return realName;
	}
	public void setRealName(String realName) {
		this.realName = realName;
	}
	public String getNickname() {
		return nickname;
	}
	public void setNickname(String nickname) {
		this.nickname = nickname;
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
	public LocalDateTime getAgreedDate() {
		return agreedDate;
	}
	public void setAgreedDate(LocalDateTime agreedDate) {
		this.agreedDate = agreedDate;
	}
	public LocalDateTime getRegistrationDate() {
		return registrationDate;
	}
	public void setRegistrationDate(LocalDateTime registrationDate) {
		this.registrationDate = registrationDate;
	}
	public LocalDateTime getWithdrawnDate() {
		return withdrawnDate;
	}
	public void setWithdrawnDate(LocalDateTime withdrawnDate) {
		this.withdrawnDate = withdrawnDate;
	}
}
