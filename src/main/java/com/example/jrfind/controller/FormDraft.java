package com.example.jrfind.controller;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.DataBinder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

// 보내지 못한 입력을 세션에 잠시 보관했다가, 같은 폼을 다시 열 때 채운다
// - 화면을 오래 열어 둬서 보낼 수 없게 된 폼 (ExpiredFormHandler): 등록·수정·댓글
// - 비회원 글 수정에서 비밀번호 확인이 끝나 있던 경우 (비밀번호를 다시 확인한 뒤 수정 화면에 채운다)
// 한 세션에 하나만, 정해진 크기 이하만 보관한다 (아무 주소로 큰 값을 계속 보내도 서버 메모리가 늘지 않도록)
// 비밀번호 칸과 CSRF 토큰은 보관하지 않는다. 고른 사진은 보관할 수 없으므로 다시 고르도록 안내한다 (imageDropped).
public record FormDraft(String uri, Map<String, String> fields, boolean imageDropped) implements Serializable {
	private static final String ATTRIBUTE = FormDraft.class.getName();
	// 본문 500자 + 물건 이름 100자 + 날짜·노선·역 등 (줄바꿈이 CRLF 로 와서 늘어나도 들어가도록 여유를 둔다)
	static final int MAX_CHARS = 2_000;
	private static final DefaultFormattingConversionService CONVERSION = new DefaultFormattingConversionService();

	// 요청의 입력값을 보관한다 (요청 주소 = 폼을 보낸 주소). 너무 크면 보관하지 않는다
	public static void save(HttpServletRequest request, boolean imageDropped) {
		Map<String, String> fields = new LinkedHashMap<>();
		int chars = 0;
		for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
			String name = entry.getKey();
			if (name.equals("_csrf") || name.toLowerCase(Locale.ROOT).contains("password") || entry.getValue().length == 0) {
				continue;
			}
			String value = entry.getValue()[0];
			chars += name.length() + value.length();
			if (chars > MAX_CHARS) {
				return;
			}
			fields.put(name, value);
		}
		request.getSession().setAttribute(ATTRIBUTE, new FormDraft(request.getRequestURI(), fields, imageDropped));
	}

	// uri 로 보낸 입력이 보관돼 있는지
	public static boolean exists(HttpServletRequest request, String uri) {
		return find(request, uri) != null;
	}

	// uri 로 보낸 입력을 꺼낸다 (한 번 꺼내면 지운다). 없으면 null
	public static FormDraft take(HttpServletRequest request, String uri) {
		FormDraft draft = find(request, uri);
		if (draft != null) {
			request.getSession().removeAttribute(ATTRIBUTE);
		}
		return draft;
	}

	private static FormDraft find(HttpServletRequest request, String uri) {
		HttpSession session = request.getSession(false);
		return (session != null && session.getAttribute(ATTRIBUTE) instanceof FormDraft draft && draft.uri().equals(uri)) ? draft : null;
	}

	public String get(String name) {
		return fields.get(name);
	}

	// 입력값을 폼 객체에 채운다 (형식이 맞지 않는 값은 비워 둔다. 검증은 다시 보낼 때 한다)
	public <T> T bindTo(T form) {
		DataBinder binder = new DataBinder(form);
		binder.setConversionService(CONVERSION);
		binder.bind(new MutablePropertyValues(fields));
		return form;
	}
}
