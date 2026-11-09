package com.example.jrfind.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.jrfind.controller.FormDraft;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

// 폼을 보냈는데 CSRF 토큰이 맞지 않을 때 (그 밖의 권한 오류는 기본 처리: 403 화면)
// 화면을 오래 열어 둬서 세션(로그인)이 끝났거나, 다른 탭에서 로그인·로그아웃해서 토큰이 바뀐 경우가 대부분이다.
// 「この操作を行う権限がありません」 대신 보던 화면으로 돌려보내고 안내한다 (?expired → 헤더 아래 안내 문구).
// 게시글 등록·수정, 댓글은 입력한 내용을 보관해 두고(FormDraft) 같은 폼을 다시 열 때 채운다.
// 로그인이 끝났으면 다시 로그인한 뒤에 채운다 (수정 화면, 댓글의 「ログイン」, 비회원 글은 비밀번호를 다시 확인한 뒤).
public class ExpiredFormHandler implements AccessDeniedHandler {
	private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
	private static final RequestMatcher LOGOUT = PATHS.matcher(HttpMethod.POST, "/logout");
	private static final RequestMatcher COMMENT = new OrRequestMatcher(
		PATHS.matcher(HttpMethod.POST, "/items/*/comments"),
		PATHS.matcher(HttpMethod.POST, "/lost-items/*/comments"));
	private static final RequestMatcher POST_FORM = new OrRequestMatcher(
		PATHS.matcher(HttpMethod.POST, "/items/new"),
		PATHS.matcher(HttpMethod.POST, "/items/*/edit"),
		PATHS.matcher(HttpMethod.POST, "/lost-items/new"),
		PATHS.matcher(HttpMethod.POST, "/lost-items/*/edit"));

	private final AccessDeniedHandler defaultHandler = new AccessDeniedHandlerImpl();
	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException, ServletException {
		// 화면의 폼이 아닌 요청(fetch 등은 토큰을 헤더로 보낸다)은 기본 처리
		if (!(exception instanceof CsrfException) || request.getParameter("_csrf") == null) {
			defaultHandler.handle(request, response, exception);
			return;
		}
		// 로그아웃 버튼: 이미 로그아웃됐거나 다른 탭에서 상태가 바뀐 경우 → 홈으로
		if (LOGOUT.matches(request)) {
			redirectStrategy.sendRedirect(request, response, "/");
			return;
		}

		String fragment = null;
		if (COMMENT.matches(request)) {
			FormDraft.save(request, false);
			fragment = "commentForm";
		} else if (POST_FORM.matches(request)) {
			FormDraft.save(request, hasImage(request));
		}
		redirectStrategy.sendRedirect(request, response, backUrl(request, fragment));
	}

	// 사진을 골라서 보냈는지 (사진은 보관할 수 없으므로 다시 고르도록 안내한다)
	private static boolean hasImage(HttpServletRequest request) {
		try {
			Part part = request.getPart("imageFile");
			return part != null && part.getSize() > 0;
		} catch (IOException | ServletException | IllegalStateException e) {
			return false; // multipart 가 아닌 요청 등
		}
	}

	// 보던 화면(같은 사이트의 Referer) + ?expired. 알 수 없으면 홈
	private String backUrl(HttpServletRequest request, String fragment) {
		String path = "/";
		String referer = request.getHeader("Referer");
		if (referer != null) {
			try {
				URI uri = new URI(referer);
				int port = (uri.getPort() != -1) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
				if (request.getServerName().equalsIgnoreCase(uri.getHost()) && port == request.getServerPort() && isLocalPath(uri.getRawPath())) {
					path = uri.getRawPath() + ((uri.getRawQuery() != null) ? "?" + uri.getRawQuery() : "");
				}
			} catch (URISyntaxException e) {
				// 홈으로
			}
		}
		return UriComponentsBuilder.fromUriString(path)
			.replaceQueryParam("expired")
			.queryParam("expired")
			.fragment(fragment)
			.build()
			.toUriString();
	}

	// 이 사이트 안의 경로만. 「//다른 사이트/…」, 「/\다른 사이트/…」 는 브라우저가 다른 사이트 주소로 읽는다
	private static boolean isLocalPath(String path) {
		return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\");
	}
}
