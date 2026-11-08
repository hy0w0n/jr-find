package com.example.jrfind.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// 로그인한 회원 정보 (Spring Security 가 세션에 보관)
// 컨트롤러에서는 @AuthenticationPrincipal LoginMember 로 꺼내 쓴다. 로그인 전이면 null
public class LoginMember implements UserDetails {
	private final Long id;
	private final String loginId;
	private final String password;
	private final String nickname;

	public LoginMember(Long id, String loginId, String password, String nickname) {
		this.id = id;
		this.loginId = loginId;
		this.password = password;
		this.nickname = nickname;
	}

	public Long getId() {
		return id;
	}

	public String getNickname() {
		return nickname;
	}

	@Override
	public String getUsername() {
		return loginId;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
	}
}
