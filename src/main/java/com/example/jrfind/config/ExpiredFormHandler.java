package com.example.jrfind.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.jrfind.domain.LoginMember;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 폼을 보냈는데 CSRF 토큰이 맞지 않을 때 (그 밖의 권한 오류는 기본 처리: 403 화면)
// 화면을 오래 열어 둬서 세션(로그인)이 끝났거나, 다른 탭에서 로그인·로그아웃해서 토큰이 바뀐 경우가 대부분이다.
// 「この操作を行う権限がありません」 대신 보던 화면으로 돌려보내고 안내한다 (?expired → 헤더 아래 안내 문구).
// 쓰던 댓글은 다시 채운다: 로그인 상태면 바로, 로그인이 끝났으면 다시 로그인해서 댓글의 「ログイン」으로 돌아왔을 때.
public class ExpiredFormHandler implements AccessDeniedHandler {
	private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
	private static final RequestMatcher LOGOUT = PATHS.matcher(HttpMethod.POST, "/logout");
	private static final RequestMatcher COMMENT = new OrRequestMatcher(
		PATHS.matcher(HttpMethod.POST, "/items/*/comments"),
		PATHS.matcher(HttpMethod.POST, "/lost-items/*/comments"));

	private final AccessDeniedHandler defaultHandler = new AccessDeniedHandlerImpl();
	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
	private final FlashMapManager flashMapManager = new SessionFlashMapManager();

	// 로그인이 끝나서 보내지 못한 댓글을 세션에 남겨 두는 이름 (CommentController 가 다시 채울 때 사용)
	public static String pendingDraftAttribute(String commentUrl) {
		return "pendingCommentDraft:" + commentUrl;
	}

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
		String draft = request.getParameter("contents");
		if (COMMENT.matches(request) && draft != null) {
			saveDraft(request, response, draft);
			fragment = "commentForm";
		}
		redirectStrategy.sendRedirect(request, response, backUrl(request, fragment));
	}

	private void saveDraft(HttpServletRequest request, HttpServletResponse response, String draft) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof LoginMember) {
			FlashMap flashMap = new FlashMap();
			flashMap.put("commentDraft", draft);
			flashMapManager.saveOutputFlashMap(flashMap, request, response);
		} else {
			request.getSession().setAttribute(pendingDraftAttribute(request.getRequestURI()), draft);
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
				if (request.getServerName().equalsIgnoreCase(uri.getHost()) && port == request.getServerPort() && uri.getRawPath() != null) {
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
}
