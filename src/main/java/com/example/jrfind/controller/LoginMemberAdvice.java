package com.example.jrfind.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.jrfind.domain.LoginMember;

import jakarta.servlet.http.HttpServletRequest;

// 모든 화면에서 ${loginMember} 로 로그인 정보를 쓸 수 있게 한다 (헤더의 로그인/로그아웃 표시 등)
// 로그인 전이면 null
@ControllerAdvice
public class LoginMemberAdvice {

	@ModelAttribute("loginMember")
	public LoginMember loginMember(@AuthenticationPrincipal LoginMember loginMember) {
		return loginMember;
	}

	// 지금 화면의 주소: 헤더의 「ログイン」에 붙여서 로그인 후 이 화면으로 돌아오게 한다 (LoginAttemptGuard)
	// 로그인·회원가입·오류 화면과, POST 결과 화면(입력 오류 등. 같은 주소를 GET 으로 열 수 없는 경우가 있다)은 null
	@ModelAttribute("currentPath")
	public String currentPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (!"GET".equals(request.getMethod()) || uri.startsWith("/login") || uri.startsWith("/signup") || uri.startsWith("/error")) {
			return null;
		}
		String query = request.getQueryString();
		return UriComponentsBuilder.fromUriString((query == null) ? uri : uri + "?" + query)
			// 보내지 못했다·로그아웃했다·탈퇴했다는 안내는 로그인 후에는 다시 보여주지 않는다
			.replaceQueryParam("expired")
			.replaceQueryParam("logout")
			.replaceQueryParam("withdrawn")
			.build()
			.toUriString();
	}
}
