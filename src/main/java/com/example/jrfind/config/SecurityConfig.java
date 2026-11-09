package com.example.jrfind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.service.MemberService;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, MemberService memberService, SessionRegistry sessionRegistry) throws Exception {
		// 로그인이 필요한 화면에서 로그인 화면으로 갈 때 원래 화면을 저장해 두는 곳.
		// 로그인 후 그 화면으로 돌아갈 때 주소에 ?continue 를 붙이지 않는다 (Spring Security 기본값은 붙인다)
		HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		requestCache.setMatchingRequestParameterName(null);
		LoginAttemptGuard loginAttemptGuard = new LoginAttemptGuard(memberService, requestCache);
		RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
		http
			// 探しています 게시판의 작성·수정·삭제, 댓글(두 게시판), 마이페이지(탈퇴 포함)는 회원만 가능
			// (로그인 전이면 로그인 화면으로 보내고, 로그인 후 원래 화면으로 돌아옴)
			// 그 외 화면은 로그인 없이 볼 수 있다.
			// 게시글·댓글 수정·삭제 권한(작성자 본인 / 4자리 비밀번호 확인)은 각 컨트롤러에서 확인한다.
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/lost-items/new", "/lost-items/*/edit", "/lost-items/*/delete", "/lost-items/*/resolve")
					.authenticated()
				.requestMatchers("/items/*/comments/**", "/lost-items/*/comments/**")
					.authenticated()
				.requestMatchers("/mypage/**")
					.authenticated()
				.anyRequest().permitAll())
			// 로그인 화면은 직접 만든 /login (GET), 로그인 처리(POST /login)는 Spring Security 가 담당
			// 연속으로 틀리면 잠시 로그인을 막는다 (LoginAttemptGuard: 잠금 확인은 필터, 횟수 기록은 성공·실패 처리)
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("loginId")
				.passwordParameter("password")
				.successHandler(loginAttemptGuard)
				.failureHandler(loginAttemptGuard))
			.addFilterBefore(loginAttemptGuard, UsernamePasswordAuthenticationFilter.class)
			.requestCache(cache -> cache
				.requestCache(requestCache))
			// 로그아웃하면 홈에서 「ログアウトしました」 안내
			.logout(logout -> logout
				.logoutSuccessUrl("/?logout"))
			// 회원별 로그인 세션 목록. 탈퇴·비밀번호 변경 때 다른 브라우저·기기의 로그인도 끝낸다
			// → 그쪽은 다음 요청 때 로그아웃되고, 탈퇴면 홈(탈퇴 안내), 비밀번호 변경이면 로그인 화면(다시 로그인 안내)으로
			.sessionManagement(session -> session
				.maximumSessions(-1) // 동시 로그인 수는 제한하지 않음
				.sessionRegistry(sessionRegistry)
				.expiredSessionStrategy(event -> {
					LoginMember member = (LoginMember) event.getSessionInformation().getPrincipal();
					String url = memberService.isWithdrawn(member.getId()) ? "/?withdrawn" : "/login?reauth";
					redirectStrategy.sendRedirect(event.getRequest(), event.getResponse(), url);
				}))
			// 로그인이 필요한 화면에 로그인 없이 들어오면 안내 문구가 있는 로그인 화면으로
			// 폼을 오래 열어 둬서 보낼 수 없게 된 경우(CSRF 토큰 불일치)는 403 대신 보던 화면으로 돌려보내고 안내 (ExpiredFormHandler)
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login?required"))
				.accessDeniedHandler(new ExpiredFormHandler()))
			// POST 요청에는 CSRF 토큰이 필요하다 (th:action 폼은 자동으로 들어감, fetch 는 헤더로 보냄)
			// H2 콘솔은 자체 화면이라 토큰을 넣을 수 없으므로 제외 (localhost 에서만 접속 가능)
			.csrf(csrf -> csrf
				.ignoringRequestMatchers("/h2-console/**"))
			// H2 콘솔이 iframe 을 사용하므로 같은 사이트 안에서는 허용
			.headers(headers -> headers
				.frameOptions(frame -> frame.sameOrigin()));
		return http.build();
	}

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	// 세션이 끝나거나 세션 ID 가 바뀌면 SessionRegistry 에 알려 준다 (끝난 세션이 목록에 쌓이지 않도록)
	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	// 회원 비밀번호, 비회원 게시글 비밀번호 모두 BCrypt 로 저장
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
