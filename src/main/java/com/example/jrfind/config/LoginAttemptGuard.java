package com.example.jrfind.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import com.example.jrfind.service.MemberService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 로그인 연속 실패 제한 (SecurityConfig 에 등록, 횟수·잠금 기준은 MemberService)
// 1. 로그인 요청(POST /login)이 오면 먼저 잠긴 상태인지 확인 → 잠겼으면 비밀번호를 확인하지 않고 /login?locked
// 2. 로그인 실패 → 실패 횟수 기록 (이번 실패로 잠기면 /login?locked, 아니면 /login?error). 입력한 ID 는 다시 채운다
// 3. 로그인 성공 → 그 ID 의 실패 횟수를 지우고 원래 가려던 화면으로
//    - 헤더의 「ログイン」으로 온 경우: 그때 보고 있던 화면 (next)
//    - 로그인이 필요한 화면에서 온 경우: 그 화면 (Spring Security 가 저장해 둔 요청)
// 접속 IP 는 request.getRemoteAddr() (X-Forwarded-For 같은 헤더는 요청하는 쪽이 바꿀 수 있으므로 쓰지 않음)
public class LoginAttemptGuard extends OncePerRequestFilter implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
	private static final String LOGIN_ID_PARAMETER = "loginId"; // SecurityConfig 의 usernameParameter
	private static final String NEXT_PARAMETER = "next"; // 로그인 화면의 hidden 값 (헤더의 「ログイン」 링크에서 넘어옴)
	private static final RequestMatcher LOGIN_REQUEST = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login");

	private final MemberService memberService;
	private final RequestCache requestCache;
	private final SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
	private final FlashMapManager flashMapManager = new SessionFlashMapManager();

	public LoginAttemptGuard(MemberService memberService, RequestCache requestCache) {
		this.memberService = memberService;
		this.requestCache = requestCache;
		this.successHandler.setRequestCache(requestCache);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (LOGIN_REQUEST.matches(request)
				&& memberService.isLoginLocked(request.getParameter(LOGIN_ID_PARAMETER), request.getRemoteAddr())) {
			backToLoginPage(request, response, "locked");
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
		backToLoginPage(request, response, locked ? "locked" : "error");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		memberService.recordLoginSuccess(request.getParameter(LOGIN_ID_PARAMETER), request.getRemoteAddr());
		String next = safeNext(request);
		if (next != null) {
			requestCache.removeRequest(request, response);
			redirectStrategy.sendRedirect(request, response, next);
			return;
		}
		successHandler.onAuthenticationSuccess(request, response, authentication); // 저장된 요청이 있으면 그 화면, 없으면 홈
	}

	// 로그인 화면으로 돌려보낸다: 입력한 ID 는 다시 채우고, 돌아갈 화면(next)은 그대로 넘긴다
	private void backToLoginPage(HttpServletRequest request, HttpServletResponse response, String result) throws IOException {
		FlashMap flashMap = new FlashMap();
		flashMap.put("loginId", request.getParameter(LOGIN_ID_PARAMETER));
		flashMapManager.saveOutputFlashMap(flashMap, request, response);

		String next = safeNext(request);
		String url = "/login?" + result + ((next != null) ? "&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8) : "");
		redirectStrategy.sendRedirect(request, response, url);
	}

	// 이 사이트 안의 주소만 돌아갈 곳으로 쓴다 (다른 사이트로 보내는 주소를 넣어 보내는 경우 대비)
	static String safeNext(HttpServletRequest request) {
		String next = request.getParameter(NEXT_PARAMETER);
		if (next == null || !next.startsWith("/") || next.startsWith("//") || next.contains("\\")
				|| next.chars().anyMatch(Character::isISOControl)
				|| next.startsWith("/login") || next.startsWith("/logout")) {
			return null;
		}
		return next;
	}
}
