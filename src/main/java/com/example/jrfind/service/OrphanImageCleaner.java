package com.example.jrfind.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.jrfind.repository.JrfindRepository;
import com.example.jrfind.repository.LostItemRepository;

// 업로드 폴더에서 어느 게시글도 쓰지 않는 사진 파일을 지운다 (매일 새벽 4시)
// 수정·삭제는 사진 파일을 정리하지만, 저장 도중에 서버가 멈추는 등으로 남은 파일이 공개된 채 쌓이지 않도록.
// 방금 올라와서 아직 DB 에 반영되기 전인 파일을 지우지 않도록, MIN_AGE 보다 오래된 파일만 지운다.
@Component
public class OrphanImageCleaner {
	private static final Logger log = LoggerFactory.getLogger(OrphanImageCleaner.class);
	static final Duration MIN_AGE = Duration.ofHours(1);

	private final ImageStorage imageStorage;
	private final JrfindRepository foundRepository;
	private final LostItemRepository lostRepository;
	private final Clock clock;

	@Autowired
	public OrphanImageCleaner(ImageStorage imageStorage, JrfindRepository foundRepository, LostItemRepository lostRepository) {
		this(imageStorage, foundRepository, lostRepository, Clock.systemUTC());
	}

	// 테스트에서 시간을 정할 때 사용
	OrphanImageCleaner(ImageStorage imageStorage, JrfindRepository foundRepository, LostItemRepository lostRepository, Clock clock) {
		this.imageStorage = imageStorage;
		this.foundRepository = foundRepository;
		this.lostRepository = lostRepository;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 4 * * *")
	public void cleanUp() {
		Path dir = imageStorage.getUploadDir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		Set<String> inUse = new HashSet<>(foundRepository.findImagePaths());
		inUse.addAll(lostRepository.findImagePaths());
		Instant threshold = clock.instant().minus(MIN_AGE);

		List<Path> orphans;
		try (Stream<Path> files = Files.list(dir)) {
			orphans = files.filter(Files::isRegularFile)
				.filter(file -> !inUse.contains(file.getFileName().toString()))
				.filter(file -> isOlderThan(file, threshold))
				.toList();
		} catch (IOException e) {
			log.warn("업로드 폴더를 읽지 못함: {}", dir, e);
			return;
		}
		orphans.forEach(file -> imageStorage.delete(file.getFileName().toString()));
		if (!orphans.isEmpty()) {
			log.info("쓰지 않는 사진 파일 {}개를 지움", orphans.size());
		}
	}

	private static boolean isOlderThan(Path file, Instant threshold) {
		try {
			return Files.getLastModifiedTime(file).toInstant().isBefore(threshold);
		} catch (IOException e) {
			return false;
		}
	}
}
