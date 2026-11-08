package com.example.jrfind.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 업로드 이미지 저장 담당
@Component
public class ImageStorage {
	private final Path uploadDir;

	public ImageStorage(@Value("${jrfind.upload-dir}") String uploadDir) {
		this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	public Path getUploadDir() {
		return uploadDir;
	}

	// 이미지를 저장하고 저장된 파일명을 반환한다. 파일이 없으면 null
	public String store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return null;
		}

		String extension = detectImageExtension(file);
		if (extension == null) {
			throw new InvalidPostException("画像ファイル(JPG・PNG・GIF・WEBP)のみアップロードできます。");
		}

		// 저장 파일명: {날짜시간}_{랜덤8자}.{확장자} (예: 20260119153012_1a2b3c4d.jpg)
		// 원본 파일명은 "../" 같은 경로 조작에 쓰일 수 있으므로 사용하지 않는다.
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String storeFilename = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

		Files.createDirectories(uploadDir);
		try (InputStream in = file.getInputStream()) {
			Files.copy(in, uploadDir.resolve(storeFilename));
		}
		return storeFilename;
	}

	// 파일 앞부분(매직 넘버)으로 실제 이미지 형식을 판별한다.
	// 확장자나 Content-Type 은 클라이언트가 마음대로 바꿀 수 있으므로 믿지 않는다.
	private String detectImageExtension(MultipartFile file) throws IOException {
		byte[] header;
		try (InputStream in = file.getInputStream()) {
			header = in.readNBytes(12);
		}

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
