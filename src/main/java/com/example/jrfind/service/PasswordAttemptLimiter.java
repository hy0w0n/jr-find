package com.example.jrfind.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// 비밀번호 확인 시도 횟수 제한
// 비회원 게시글 비밀번호는 숫자 4자리(1만 가지)라서 제한이 없으면 전부 대입해 볼 수 있다.
// 같은 key(게시글+접속자, 마이페이지는 회원)로 MAX_FAILURES 번 틀리면 LOCK_TIME 동안 확인을 막는다.
@Component
public class PasswordAttemptLimiter {
	private static final int MAX_FAILURES = 5;
	private static final Duration LOCK_TIME = Duration.ofMinutes(5);

	private record Attempt(int failures, Instant lockedUntil) {}

	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

	public boolean isLocked(String key) {
		Attempt attempt = attempts.get(key);
		if (attempt == null || attempt.lockedUntil() == null) {
			return false;
		}
		if (Instant.now().isAfter(attempt.lockedUntil())) {
			attempts.remove(key); // 잠금 시간이 지나면 횟수 초기화
			return false;
		}
		return true;
	}

	public void recordFailure(String key) {
		attempts.compute(key, (k, attempt) -> {
			int failures = (attempt == null) ? 1 : attempt.failures() + 1;
			Instant lockedUntil = (failures >= MAX_FAILURES) ? Instant.now().plus(LOCK_TIME) : null;
			return new Attempt(failures, lockedUntil);
		});
	}

	public void reset(String key) {
		attempts.remove(key);
	}
}
