package com.example.jrfind.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// CSRF 토큰 (화면의 JS 가 POST 하기 직전에 받아 간다)
// 토큰을 만들면 세션이 생기므로, 비회원이 글을 보기만 하는 화면(見つけた 상세)에는 토큰을 넣지 않고
// 비밀번호를 보낼 때 여기서 받는다 (글을 보기만 하는 방문자·검색 로봇마다 세션이 쌓이지 않도록)
@RestController
public class CsrfController {

	public record CsrfResponse(String headerName, String parameterName, String token) {
	}

	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
	}
}
