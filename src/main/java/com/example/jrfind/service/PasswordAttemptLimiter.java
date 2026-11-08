package com.example.jrfind.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// 비밀번호 확인 시도 횟수 제한
// 비회원 게시글 비밀번호는 숫자 4자리(1만 가지)라서 제한이 없으면 전부 대입해 볼 수 있다.
// 같은 key(게시글+접속자, 마이페이지는 회원, 로그인은 ID+접속자와 접속자)로 정해진 횟수(기본 MAX_FAILURES)만큼
// 틀리면 LOCK_TIME 동안 확인을 막는다. 마지막으로 틀린 뒤 LOCK_TIME 이 지나면 횟수를 처음부터 센다.
@Component
public class PasswordAttemptLimiter {
	private static final int MAX_FAILURES = 5;
	private static final Duration LOCK_TIME = Duration.ofMinutes(5);
	// 기록이 이만큼 쌓이면 기간이 지난 기록을 지운다 (로그인 ID 처럼 key 를 요청하는 쪽이 정할 수 있는 경우 대비)
	private static final int CLEANUP_THRESHOLD = 10_000;

	private record Attempt(int failures, Instant lastFailure, Instant lockedUntil) {
		boolean isExpired(Instant now) {
			Instant end = (lockedUntil != null) ? lockedUntil : lastFailure.plus(LOCK_TIME);
			return now.isAfter(end);
		}
	}

	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
	private final Clock clock;

	public PasswordAttemptLimiter() {
		this(Clock.systemUTC());
	}

	// 테스트에서 시간을 직접 움직일 때 사용
	PasswordAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	public boolean isLocked(String key) {
		Attempt attempt = attempts.get(key);
		if (attempt == null) {
			return false;
		}
		if (attempt.isExpired(clock.instant())) {
			attempts.remove(key, attempt); // 잠금 시간이 지나면 횟수 초기화
			return false;
		}
		return attempt.lockedUntil() != null;
	}

	// 이번 실패로 잠기면 true
	public boolean recordFailure(String key) {
		return recordFailure(key, MAX_FAILURES);
	}

	public boolean recordFailure(String key, int maxFailures) {
		Instant now = clock.instant();
		if (attempts.size() >= CLEANUP_THRESHOLD) {
			attempts.values().removeIf(attempt -> attempt.isExpired(now));
		}
		Attempt updated = attempts.compute(key, (k, attempt) -> {
			int failures = (attempt == null || attempt.isExpired(now)) ? 1 : attempt.failures() + 1;
			Instant lockedUntil = (failures >= maxFailures) ? now.plus(LOCK_TIME) : null;
			return new Attempt(failures, now, lockedUntil);
		});
		return updated.lockedUntil() != null;
	}

	public void reset(String key) {
		attempts.remove(key);
	}

	// 테스트 확인용
	int size() {
		return attempts.size();
	}
}
