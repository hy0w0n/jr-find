package com.example.jrfind.config;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.jrfind.service.MemberService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 로그인 연속 실패 제한 (SecurityConfig 에 등록, 횟수·잠금 기준은 MemberService)
// 1. 로그인 요청(POST /login)이 오면 먼저 잠긴 상태인지 확인 → 잠겼으면 비밀번호를 확인하지 않고 /login?locked
// 2. 로그인 실패 → 실패 횟수 기록 (이번 실패로 잠기면 /login?locked, 아니면 /login?error)
// 3. 로그인 성공 → 그 ID 의 실패 횟수를 지우고 원래 가려던 화면으로
// 접속 IP 는 request.getRemoteAddr() (X-Forwarded-For 같은 헤더는 요청하는 쪽이 바꿀 수 있으므로 쓰지 않음)
public class LoginAttemptGuard extends OncePerRequestFilter implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
	private static final String LOGIN_ID_PARAMETER = "loginId"; // SecurityConfig 의 usernameParameter
	private static final RequestMatcher LOGIN_REQUEST = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login");

	private final MemberService memberService;
	private final AuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	public LoginAttemptGuard(MemberService memberService) {
		this.memberService = memberService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (LOGIN_REQUEST.matches(request)
				&& memberService.isLoginLocked(request.getParameter(LOGIN_ID_PARAMETER), request.getRemoteAddr())) {
			redirectStrategy.sendRedirect(request, response, "/login?locked");
			return;
		}
		chain.doFilter(request, response);
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		boolean locked = false;
		// ID·비밀번호가 틀린 경우만 센다 (없는 ID 도 BadCredentialsException 으로 온다. DB 오류 등은 세지 않음)
		if (exception instanceof BadCredentialsException) {
			locked = memberService.recordLoginFailure(request.getParameter(LOGIN_ID_PARAMETER), request.getRemoteAddr());
		}
		redirectStrategy.sendRedirect(request, response, locked ? "/login?locked" : "/login?error");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		memberService.recordLoginSuccess(request.getParameter(LOGIN_ID_PARAMETER), request.getRemoteAddr());
		successHandler.onAuthenticationSuccess(request, response, authentication); // 기본 동작과 같음 (원래 화면 또는 홈)
	}
}
