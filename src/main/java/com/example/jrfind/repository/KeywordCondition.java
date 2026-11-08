package com.example.jrfind.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// 검색어 조건 (見つけた / 探しています 게시판 공통)
// 제목(날짜·노선·역·카테고리·물건 이름이 들어 있음)이나 본문에 검색어가 들어 있는 글.
// 공백(전각 공백 포함)으로 나눈 단어는 모두 포함해야 한다 (AND). 대소문자는 구분하지 않는다.
final class KeywordCondition {
	// 단어가 너무 많으면 앞에서부터 이 개수까지만 사용
	static final int MAX_WORDS = 5;

	private KeywordCondition() {
	}

	static void append(StringBuilder sql, List<Object> params, String keyword) {
		for (String word : words(keyword)) {
			sql.append(" AND (LOWER(Title) LIKE ? ESCAPE '\\' OR LOWER(Contents) LIKE ? ESCAPE '\\')");
			String pattern = "%" + escapeLike(word.toLowerCase(Locale.ROOT)) + "%";
			params.add(pattern);
			params.add(pattern);
		}
	}

	static List<String> words(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}
		return Arrays.stream(keyword.strip().split("[\\s\\u3000]+"))
			.limit(MAX_WORDS)
			.toList();
	}

	// 검색어에 들어 있는 % _ 는 와일드카드가 아니라 글자 그대로 찾는다
	private static String escapeLike(String word) {
		return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
