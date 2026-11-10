package com.example.jrfind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jrfind.repository.JrfindRepository;
import com.example.jrfind.repository.LostItemRepository;

// 쓰지 않는 사진 파일 정리: 어느 게시판에서도 쓰지 않고, 1시간 이상 지난 파일만 지운다
class OrphanImageCleanerTest {
	@TempDir
	Path uploadDir;

	private final Instant now = Instant.parse("2026-02-01T04:00:00Z");

	@Test
	void 쓰지_않는_오래된_파일만_지운다() throws IOException {
		Path found = file("found.jpg", Duration.ofDays(3));
		Path lost = file("lost.png", Duration.ofDays(3));
		Path orphan = file("orphan.jpg", Duration.ofDays(3));
		Path justUploaded = file("uploading.jpg", Duration.ofMinutes(5)); // 저장 직전일 수 있다
		Path subDir = Files.createDirectories(uploadDir.resolve("sub"));

		JrfindRepository foundRepository = mock(JrfindRepository.class);
		LostItemRepository lostRepository = mock(LostItemRepository.class);
		when(foundRepository.findImagePaths()).thenReturn(List.of("found.jpg"));
		when(lostRepository.findImagePaths()).thenReturn(List.of("lost.png"));

		new OrphanImageCleaner(new ImageStorage(uploadDir.toString()), foundRepository, lostRepository,
			Clock.fixed(now, ZoneOffset.UTC)).cleanUp();

		assertThat(found).exists();
		assertThat(lost).exists();
		assertThat(orphan).doesNotExist();
		assertThat(justUploaded).exists();
		assertThat(subDir).exists();
	}

	@Test
	void 업로드_폴더가_없으면_아무_일도_하지_않는다() {
		JrfindRepository foundRepository = mock(JrfindRepository.class);
		LostItemRepository lostRepository = mock(LostItemRepository.class);

		new OrphanImageCleaner(new ImageStorage(uploadDir.resolve("none").toString()), foundRepository, lostRepository,
			Clock.fixed(now, ZoneOffset.UTC)).cleanUp();

		assertThat(uploadDir.resolve("none")).doesNotExist();
	}

	private Path file(String name, Duration age) throws IOException {
		Path file = Files.writeString(uploadDir.resolve(name), "x");
		Files.setLastModifiedTime(file, FileTime.from(now.minus(age)));
		return file;
	}
}
