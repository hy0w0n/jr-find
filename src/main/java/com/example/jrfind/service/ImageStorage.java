package com.example.jrfind.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 업로드 이미지 저장·삭제 담당
@Component
public class ImageStorage {
	private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

	private final Path uploadDir;

	public ImageStorage(@Value("${jrfind.upload-dir}") String uploadDir) {
		this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	public Path getUploadDir() {
		return uploadDir;
	}

	// 이미지를 저장하고 저장된 파일명을 반환한다. 파일이 없으면 null
	// 큰 사진은 줄이고, JPG 의 촬영 정보(위치 등)는 지운 뒤 저장한다 (ImageResizer)
	public String store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return null;
		}

		byte[] original = file.getBytes();
		String extension = detectImageExtension(original);
		if (extension == null) {
			throw new InvalidPostException("画像ファイル(JPG・PNG・GIF・WEBP)のみアップロードできます。");
		}
		byte[] content = ImageResizer.process(original, extension);

		// 저장 파일명: {날짜시간}_{랜덤8자}.{확장자} (예: 20260119153012_1a2b3c4d.jpg)
		// 원본 파일명은 "../" 같은 경로 조작에 쓰일 수 있으므로 사용하지 않는다.
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String storeFilename = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

		Files.createDirectories(uploadDir);
		Files.write(uploadDir.resolve(storeFilename), content);
		return storeFilename;
	}

	// 저장된 이미지 파일을 지운다 (사진을 바꾸거나 지웠을 때, 게시글을 삭제했을 때). 파일이 없으면 아무 일도 하지 않는다.
	// 파일 정리는 부가 작업이므로 실패해도 게시글 처리는 그대로 두고 로그만 남긴다.
	public void delete(String filename) {
		if (filename == null || filename.isBlank()) {
			return;
		}
		try {
			Path file = uploadDir.resolve(filename).normalize();
			// 업로드 폴더 바로 아래의 파일만 지운다 (DB 값에 "../" 등이 들어 있어도 다른 파일을 지우지 않도록)
			if (!uploadDir.equals(file.getParent())) {
				log.warn("업로드 폴더 밖의 파일은 지우지 않음: {}", filename);
				return;
			}
			Files.deleteIfExists(file);
		} catch (IOException | InvalidPathException e) {
			log.warn("이미지 파일 삭제 실패: {}", filename, e);
		}
	}

	// 파일 앞부분(매직 넘버)으로 실제 이미지 형식을 판별한다.
	// 확장자나 Content-Type 은 클라이언트가 마음대로 바꿀 수 있으므로 믿지 않는다.
	private String detectImageExtension(byte[] data) {
		byte[] header = Arrays.copyOf(data, Math.min(data.length, 12));

		if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
			return "jpg";
		}
		if (startsWith(header, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "png";
		}
		if (startsWith(header, 'G', 'I', 'F', '8')) {
			return "gif";
		}
		if (startsWith(header, 'R', 'I', 'F', 'F') && header.length >= 12
				&& "WEBP".equals(new String(Arrays.copyOfRange(header, 8, 12), StandardCharsets.US_ASCII))) {
			return "webp";
		}
		return null;
	}

	private boolean startsWith(byte[] data, int... prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if ((data[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}
		return true;
	}
}
