package com.example.jrfind.domain;

// 입력 문자열 정리 (회원 이름, 댓글·게시글 본문)
public final class Texts {
	private Texts() {
	}

	// 브라우저는 줄바꿈을 CRLF(2글자)로 보내지만, 입력 칸의 maxlength 와 글자 수 표시는 1글자로 센다.
	// LF 로 맞춰서 화면과 같은 기준으로 글자 수를 검사한다.
	public static String normalizeNewlines(String text) {
		return (text == null) ? null : text.replace("\r\n", "\n").replace('\r', '\n');
	}

	// 앞뒤 공백 제거. String.strip() 이 지우지 않는 NBSP, 폭 없는 공백(U+200B 등)까지 지운다
	public static String strip(String text) {
		if (text == null) {
			return null;
		}
		int start = 0;
		int end = text.length();
		while (start < end && isInvisible(text.codePointAt(start))) {
			start += Character.charCount(text.codePointAt(start));
		}
		while (end > start && isInvisible(text.codePointBefore(end))) {
			end -= Character.charCount(text.codePointBefore(end));
		}
		return text.substring(start, end);
	}

	// 공백과 보이지 않는 글자를 모두 뺀 문자열 (사용할 수 없는 이름인지 비교할 때)
	public static String removeInvisible(String text) {
		StringBuilder visible = new StringBuilder();
		text.codePoints().filter(c -> !isInvisible(c)).forEach(visible::appendCodePoint);
		return visible.toString();
	}

	// 공백(전각 공백·NBSP 포함)과 폭 없는 글자
	private static boolean isInvisible(int c) {
		return Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT;
	}
}
