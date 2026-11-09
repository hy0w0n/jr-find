package com.example.jrfind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// 이미지 파일 삭제: 업로드 폴더 안의 파일만 지우고, 지울 수 없어도 예외를 던지지 않는다
class ImageStorageTest {
	@TempDir
	Path temp;

	private Path uploadDir;
	private ImageStorage storage;

	@BeforeEach
	void setUp() throws IOException {
		uploadDir = Files.createDirectories(temp.resolve("uploads"));
		storage = new ImageStorage(uploadDir.toString());
	}

	@Test
	void 업로드_폴더의_파일을_지운다() throws IOException {
		Path file = Files.writeString(uploadDir.resolve("20260128150748_umbrella(2).jpg"), "x");

		storage.delete("20260128150748_umbrella(2).jpg");

		assertThat(file).doesNotExist();
	}

	@Test
	void 없는_파일이나_빈_값은_그냥_넘어간다() {
		assertThatCode(() -> {
			storage.delete("none.jpg");
			storage.delete(null);
			storage.delete("");
			storage.delete("a\u0000b.jpg"); // 경로로 쓸 수 없는 문자
		}).doesNotThrowAnyException();
	}

	@Test
	void 업로드_폴더_밖의_파일은_지우지_않는다() throws IOException {
		Path outside = Files.writeString(temp.resolve("secret.txt"), "x");
		Path inSubDir = Files.writeString(Files.createDirectories(uploadDir.resolve("sub")).resolve("a.jpg"), "x");

		storage.delete("../secret.txt");
		storage.delete("sub/../../secret.txt");
		storage.delete(outside.toString()); // 절대 경로
		storage.delete("sub/a.jpg");        // 하위 폴더

		assertThat(outside).exists();
		assertThat(inSubDir).exists();
	}
}
