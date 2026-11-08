package com.example.jrfind.controller;

import org.springframework.util.StringUtils;

// 검색어(q) 정리: 앞뒤 공백 제거, 공백만 있으면 검색어 없음(null)
final class SearchKeyword {
	private SearchKeyword() {
	}

	static String normalize(String keyword) {
		return StringUtils.hasText(keyword) ? keyword.strip() : null;
	}
}
