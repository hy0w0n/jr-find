package com.example.jrfind.repository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

// 검색어 조건 (見つけた / 探しています 게시판 공통)
// 제목(날짜·노선·역·카테고리·물건 이름이 들어 있음)이나 본문에 검색어가 들어 있는 글.
// 공백(전각 공백 포함)으로 나눈 단어는 모두 포함해야 한다 (AND). 대소문자는 구분하지 않는다.
// 전각·반각도 구분하지 않는다: 검색어는 NFKC 로 맞추고(ｽﾏﾎ → スマホ, Ｓｕｉｃａ → Suica),
// 글 쪽의 전각 영숫자는 TRANSLATE 로 반각으로 바꿔서 비교한다.
public final class KeywordCondition {
	// 단어가 너무 많으면 앞에서부터 이 개수까지만 사용 (화면에서 안내: isTruncated)
	public static final int MAX_WORDS = 5;

	private static final String FULL_WIDTH_ALNUM = "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ０１２３４５６７８９";
	private static final String HALF_WIDTH_ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final String TITLE = "LOWER(TRANSLATE(Title, '" + FULL_WIDTH_ALNUM + "', '" + HALF_WIDTH_ALNUM + "'))";
	private static final String CONTENTS = "LOWER(TRANSLATE(Contents, '" + FULL_WIDTH_ALNUM + "', '" + HALF_WIDTH_ALNUM + "'))";

	private KeywordCondition() {
	}

	static void append(StringBuilder sql, List<Object> params, String keyword) {
		for (String word : words(keyword)) {
			// ① 영숫자만 반각으로 바꾼 단어: 「（予定）」처럼 전각 기호가 들어 있는 글도 그대로 찾는다
			// ② NFKC 로 바꾼 단어: 반각 카나(ｽﾏﾎ) 등 (NFKC 는 전각 기호도 반각으로 바꾸므로 ①과 함께 쓴다)
			Set<String> patterns = new LinkedHashSet<>();
			patterns.add(likePattern(toHalfWidthAlnum(word)));
			patterns.add(likePattern(Normalizer.normalize(word, Normalizer.Form.NFKC)));
			List<String> conditions = new ArrayList<>();
			for (String pattern : patterns) {
				conditions.add(TITLE + " LIKE ? ESCAPE '\\' OR " + CONTENTS + " LIKE ? ESCAPE '\\'");
				params.add(pattern);
				params.add(pattern);
			}
			sql.append(" AND (" + String.join(" OR ", conditions) + ")");
		}
	}

	private static String likePattern(String word) {
		return "%" + escapeLike(word.toLowerCase(Locale.ROOT)) + "%";
	}

	// 글 쪽의 TRANSLATE 와 같은 변환
	private static String toHalfWidthAlnum(String word) {
		StringBuilder result = new StringBuilder(word.length());
		for (char c : word.toCharArray()) {
			int index = FULL_WIDTH_ALNUM.indexOf(c);
			result.append(index >= 0 ? HALF_WIDTH_ALNUM.charAt(index) : c);
		}
		return result.toString();
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
