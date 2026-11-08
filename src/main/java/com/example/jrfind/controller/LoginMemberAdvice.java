package com.example.jrfind.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.jrfind.domain.LoginMember;

// 모든 화면에서 ${loginMember} 로 로그인 정보를 쓸 수 있게 한다 (헤더의 로그인/로그아웃 표시 등)
// 로그인 전이면 null
@ControllerAdvice
public class LoginMemberAdvice {

	@ModelAttribute("loginMember")
	public LoginMember loginMember(@AuthenticationPrincipal LoginMember loginMember) {
		return loginMember;
	}
}
