package com.example.jrfind.controller;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

// 같은 내용을 짧은 시간 안에 두 번 보내면 한 번만 저장한다 (게시글 등록, 댓글)
// - 등록한 뒤 브라우저 「뒤로」로 돌아오면 입력한 내용이 그대로 남아 있어서(브라우저가 다시 채운다) 그대로 다시 보내기 쉽다
// - 버튼을 거의 동시에 여러 번 눌러 요청이 여러 개 간 경우
// 접속자(세션)별로, 보낸 곳(주소)과 내용이 같은 요청을 DUPLICATE_WINDOW 동안 기억한다.
@Component
@SessionScope
public class SubmittedForms implements Serializable {
	private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(10);
	private static final int MAX_ENTRIES = 50;

	// 키 → 보낸 시각 (오래된 순)
	private final Map<String, Instant> submitted = new LinkedHashMap<>();

	// 처음 보내는 내용이면 기록하고 true, 같은 내용을 DUPLICATE_WINDOW 안에 이미 보냈으면 false
	public synchronized boolean begin(String key) {
		Instant now = Instant.now();
		submitted.values().removeIf(time -> time.plus(DUPLICATE_WINDOW).isBefore(now));
		if (submitted.containsKey(key)) {
			return false;
		}
		submitted.put(key, now);
		if (submitted.size() > MAX_ENTRIES) {
			Iterator<String> oldest = submitted.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
		return true;
	}

	// 저장하지 못했으면 기록을 지운다 (같은 내용을 다시 보낼 수 있도록)
	public synchronized void cancel(String key) {
		submitted.remove(key);
	}

	// 보낸 곳과 내용으로 만든 키 (본문은 길 수 있으므로 해시로 기억한다)
	public static String key(String uri, Object... values) {
		StringBuilder text = new StringBuilder(uri);
		for (Object value : values) {
			text.append('\u0000').append(Objects.toString(value, ""));
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // SHA-256 은 모든 Java 에 있다
		}
	}
}
