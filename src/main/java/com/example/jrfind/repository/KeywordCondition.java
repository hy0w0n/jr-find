package com.example.jrfind.repository;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 검색어 조건 (見つけた / 探しています 게시판 공통)
// 제목(날짜·노선·역·카테고리·물건 이름이 들어 있음)이나 본문에 검색어가 들어 있는 글.
// 공백(전각 공백 포함)으로 나눈 단어는 모두 포함해야 한다 (AND). 대소문자는 구분하지 않는다.
// 전각·반각도 구분하지 않는다: 글은 저장할 때 제목+본문을 NFKC·소문자로 맞춰 SEARCHTEXT 에 넣어 두고(ｽﾏﾎ → スマホ, Ｓｕｉｃａ → suica),
// 검색어도 같은 방법으로 맞춰서 비교한다. (예전에는 검색할 때마다 모든 글의 제목·본문을 변환해서 느렸다)
public final class KeywordCondition {
	// 단어가 너무 많으면 앞에서부터 이 개수까지만 사용 (화면에서 안내: isTruncated)
	public static final int MAX_WORDS = 5;

	private KeywordCondition() {
	}

	// SEARCHTEXT 에 저장할 값. 줄바꿈으로 이으므로 (검색어에는 공백이 없다) 제목 끝과 본문 앞이 이어져 찾아지는 일은 없다
	public static String searchText(String title, String contents) {
		return normalize(title + "\n" + contents);
	}

	static void append(StringBuilder sql, List<Object> params, String keyword) {
		// 맞춘 뒤 같은 단어는 한 번만 (「ｽﾏﾎ スマホ」)
		Set<String> normalizedWords = new LinkedHashSet<>();
		for (String word : words(keyword)) {
			normalizedWords.add(normalize(word));
		}
		for (String word : normalizedWords) {
			sql.append(" AND SearchText LIKE ? ESCAPE '\\'");
			params.add("%" + escapeLike(word) + "%");
		}
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	static List<String> words(String keyword) {
		return allWords(keyword).stream().limit(MAX_WORDS).toList();
	}

	// 단어가 MAX_WORDS 개보다 많아서 뒤의 단어를 쓰지 않았는지
	public static boolean isTruncated(String keyword) {
		return allWords(keyword).size() > MAX_WORDS;
	}

	private static List<String> allWords(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}
		return Arrays.asList(keyword.strip().split("[\\s\\u3000]+"));
	}

	// 검색어에 들어 있는 % _ 는 와일드카드가 아니라 글자 그대로 찾는다
	private static String escapeLike(String word) {
		return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
