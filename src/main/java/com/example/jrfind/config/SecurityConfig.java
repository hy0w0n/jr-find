package com.example.jrfind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// 探しています 게시판의 작성·수정·삭제는 회원만 가능 (로그인 전이면 로그인 화면으로 보내고, 로그인 후 원래 화면으로 돌아옴)
			// 그 외 화면은 로그인 없이 볼 수 있다.
			// 게시글 수정·삭제 권한(작성자 본인 / 4자리 비밀번호 확인)은 각 컨트롤러에서 확인한다.
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/lost-items/new", "/lost-items/*/edit", "/lost-items/*/delete", "/lost-items/*/resolve")
					.authenticated()
				.anyRequest().permitAll())
			// 로그인 화면은 직접 만든 /login (GET), 로그인 처리(POST /login)는 Spring Security 가 담당
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("loginId")
				.passwordParameter("password")
				.failureUrl("/login?error"))
			.logout(logout -> logout
				.logoutSuccessUrl("/"))
			// 로그인이 필요한 화면에 로그인 없이 들어오면 안내 문구가 있는 로그인 화면으로
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login?required")))
			// POST 요청에는 CSRF 토큰이 필요하다 (th:action 폼은 자동으로 들어감, fetch 는 헤더로 보냄)
			// H2 콘솔은 자체 화면이라 토큰을 넣을 수 없으므로 제외 (localhost 에서만 접속 가능)
			.csrf(csrf -> csrf
				.ignoringRequestMatchers("/h2-console/**"))
			// H2 콘솔이 iframe 을 사용하므로 같은 사이트 안에서는 허용
			.headers(headers -> headers
				.frameOptions(frame -> frame.sameOrigin()));
		return http.build();
	}

	// 회원 비밀번호, 비회원 게시글 비밀번호 모두 BCrypt 로 저장
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
