package com.example.jrfind.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

// 비밀번호 시도 횟수 제한: 시간을 직접 움직여서 잠금·해제를 확인
class PasswordAttemptLimiterTest {
	private final MovableClock clock = new MovableClock();
	private final PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(clock);

	@Test
	void 다섯_번_틀리면_잠기고_5분이_지나면_풀린다() {
		for (int i = 1; i <= 4; i++) {
			assertThat(limiter.recordFailure("k")).isFalse();
			assertThat(limiter.isLocked("k")).isFalse();
		}
		assertThat(limiter.recordFailure("k")).isTrue();
		assertThat(limiter.isLocked("k")).isTrue();

		clock.advance(Duration.ofMinutes(4).plusSeconds(59));
		assertThat(limiter.isLocked("k")).isTrue();
		clock.advance(Duration.ofSeconds(2));
		assertThat(limiter.isLocked("k")).isFalse();

		// 풀린 뒤에는 처음부터 다시 센다
		assertThat(limiter.recordFailure("k")).isFalse();
		assertThat(limiter.isLocked("k")).isFalse();
	}

	@Test
	void 마지막으로_틀린_뒤_5분이_지나면_횟수를_처음부터_센다() {
		for (int i = 1; i <= 4; i++) {
			limiter.recordFailure("k");
		}
		clock.advance(Duration.ofMinutes(5).plusSeconds(1));

		assertThat(limiter.recordFailure("k")).isFalse(); // 5번째가 아니라 1번째
		for (int i = 2; i <= 4; i++) {
			limiter.recordFailure("k");
		}
		assertThat(limiter.isLocked("k")).isFalse();
		assertThat(limiter.recordFailure("k")).isTrue();
	}

	@Test
	void 맞히면_횟수가_지워지고_key_마다_따로_센다() {
		for (int i = 1; i <= 4; i++) {
			limiter.recordFailure("a");
		}
		limiter.reset("a");
		for (int i = 1; i <= 4; i++) {
			limiter.recordFailure("a");
			limiter.recordFailure("b");
		}
		assertThat(limiter.isLocked("a")).isFalse();
		assertThat(limiter.recordFailure("b")).isTrue();
		assertThat(limiter.isLocked("a")).isFalse();
	}

	@Test
	void 잠기는_횟수를_key_마다_정할_수_있다() {
		for (int i = 1; i <= 19; i++) {
			assertThat(limiter.recordFailure("ip", 20)).isFalse();
		}
		assertThat(limiter.recordFailure("ip", 20)).isTrue();
		assertThat(limiter.isLocked("ip")).isTrue();
	}

	@Test
	void 기록이_많이_쌓이면_기간이_지난_기록은_지운다() {
		for (int i = 0; i < 10_000; i++) {
			limiter.recordFailure("old-" + i);
		}
		clock.advance(Duration.ofMinutes(6));

		limiter.recordFailure("new");

		assertThat(limiter.size()).isEqualTo(1);
	}

	private static class MovableClock extends Clock {
		private Instant now = Instant.parse("2026-11-09T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
