package com.example.jrfind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

// 업로드 사진 저장: 큰 사진은 줄이고, JPG 의 촬영 정보(EXIF)는 회전을 반영한 뒤 지운다
class ImageResizeTest {
	@TempDir
	Path temp;

	private ImageStorage storage;

	@BeforeEach
	void setUp() {
		storage = new ImageStorage(temp.toString());
	}

	@Test
	void 세로로_찍은_큰_JPG_는_돌려서_줄이고_촬영_정보를_지운다() throws IOException {
		// 2000×1000 (왼쪽 절반 빨강, 오른쪽 절반 파랑) + EXIF: 오른쪽으로 90° 돌려서 볼 것(6), 촬영 위치(GPS)
		byte[] photo = withExif(jpeg(halves(2000, 1000)), 6);
		assertThat(contains(photo, "Exif")).isTrue();

		BufferedImage saved = read(store(photo, "jpg"));

		assertThat(saved.getWidth()).isEqualTo(640);   // 1000×2000 으로 돌린 뒤 긴 변 1280 에 맞춤
		assertThat(saved.getHeight()).isEqualTo(1280);
		assertThat(isReddish(saved.getRGB(320, 100))).isTrue();   // 왼쪽(빨강)이 위로
		assertThat(isBluish(saved.getRGB(320, 1180))).isTrue();
		byte[] bytes = Files.readAllBytes(temp.resolve(lastStored));
		assertThat(contains(bytes, "Exif")).isFalse();
		assertThat(contains(bytes, "GPS-N")).isFalse();
	}

	@Test
	void 작은_JPG_도_촬영_정보는_지운다() throws IOException {
		byte[] photo = withExif(jpeg(halves(400, 300)), 1);

		BufferedImage saved = read(store(photo, "jpg"));

		assertThat(saved.getWidth()).isEqualTo(400);
		assertThat(saved.getHeight()).isEqualTo(300);
		assertThat(contains(Files.readAllBytes(temp.resolve(lastStored)), "Exif")).isFalse();
	}

	@Test
	void 작은_PNG_는_그대로_저장한다() throws IOException {
		byte[] png = png(halves(300, 200));

		store(png, "png");

		assertThat(Files.readAllBytes(temp.resolve(lastStored))).isEqualTo(png);
	}

	@Test
	void 큰_PNG_는_줄인다() throws IOException {
		BufferedImage saved = read(store(png(halves(3000, 1500)), "png"));

		assertThat(saved.getWidth()).isEqualTo(1280);
		assertThat(saved.getHeight()).isEqualTo(640);
	}

	@Test
	void 해상도가_터무니없이_큰_그림은_펼치기_전에_거부한다() {
		// 용량은 수십 바이트지만 30000×30000 (9억 화소) 이라고 적힌 PNG
		byte[] bomb = pngHeaderOnly(30000, 30000);

		assertThatThrownBy(() -> store(bomb, "png"))
			.isInstanceOf(InvalidPostException.class)
			.hasMessageContaining("解像度");
		assertThat(temp.toFile().list()).isEmpty();
	}

	@Test
	void 읽을_수_없는_그림은_형식만_맞으면_그대로_저장한다() throws IOException {
		byte[] fakeJpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3 };

		store(fakeJpeg, "jpg");

		assertThat(Files.readAllBytes(temp.resolve(lastStored))).isEqualTo(fakeJpeg);
	}

	@Test
	void EXIF_회전값을_읽는다() throws IOException {
		byte[] plain = jpeg(halves(40, 20));
		assertThat(ImageResizer.exifOrientation(plain)).isEqualTo(1);
		for (int orientation = 1; orientation <= 8; orientation++) {
			assertThat(ImageResizer.exifOrientation(withExif(plain, orientation))).isEqualTo(orientation);
		}
	}

	// ---- 도우미 ----

	private String lastStored;

	private String store(byte[] content, String extension) throws IOException {
		lastStored = storage.store(new MockMultipartFile("imageFile", "photo." + extension, "image/" + extension, content));
		assertThat(lastStored).endsWith("." + extension);
		return lastStored;
	}

	private BufferedImage read(String filename) throws IOException {
		BufferedImage image = ImageIO.read(temp.resolve(filename).toFile());
		assertThat(image).isNotNull();
		return image;
	}

	private static BufferedImage halves(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width / 2, height);
		g.setColor(Color.BLUE);
		g.fillRect(width / 2, 0, width - width / 2, height);
		g.dispose();
		return image;
	}

	private static byte[] jpeg(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	private static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	// 휴대폰 사진처럼 SOI 바로 뒤에 EXIF(APP1) 를 넣는다: IFD0 = Orientation + GPS IFD 위치, GPS IFD = 위도 기호 "N"
	private static byte[] withExif(byte[] jpeg, int orientation) {
		ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 * 2 + 4 + 2 + 12 + 4 + 8); // 빅엔디언 (MM)
		tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
		tiff.putShort((short) 2);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		int gpsIfd = 8 + 2 + 12 * 2 + 4;
		tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsIfd);
		tiff.putInt(0);
		tiff.putShort((short) 1);
		tiff.putShort((short) 0x0001).putShort((short) 2).putInt(6).putInt(gpsIfd + 2 + 12 + 4);
		tiff.putInt(0);
		tiff.put("GPS-N\0\0\0".getBytes(StandardCharsets.US_ASCII));

		byte[] body = tiff.array();
		int length = 2 + 6 + body.length;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(0xFF);
		out.write(0xD8);
		out.write(0xFF);
		out.write(0xE1);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(body);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	// IHDR(크기)와 IEND 만 있는 PNG
	private static byte[] pngHeaderOnly(int width, int height) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
		chunk(out, "IHDR", ihdr.array());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
		out.writeBytes(typeBytes);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}

	private static boolean contains(byte[] data, String text) {
		return new String(data, StandardCharsets.ISO_8859_1).contains(text);
	}

	private static boolean isReddish(int rgb) {
		Color c = new Color(rgb);
		return c.getRed() > 200 && c.getBlue() < 60;
	}

	private static boolean isBluish(int rgb) {
		Color c = new Color(rgb);
		return c.getBlue() > 200 && c.getRed() < 60;
	}

	@Test
	void 줄인_JPG_를_다시_읽을_수_있다() throws IOException {
		byte[] saved = Files.readAllBytes(temp.resolve(store(jpeg(halves(3000, 2000)), "jpg")));
		assertThat(ImageIO.read(new ByteArrayInputStream(saved)).getWidth()).isEqualTo(1280);
		assertThat(saved.length).isLessThan(jpeg(halves(3000, 2000)).length);
	}
}
