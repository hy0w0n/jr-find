package com.example.jrfind.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

// 업로드 사진을 화면에 맞는 크기로 줄인다 (ImageStorage 에서 사용)
// - 긴 변이 MAX_EDGE 를 넘으면 줄인다 (상세 화면은 최대 666px. 고해상도 화면 2배까지). 9.4MB 사진을 그대로 보내던 문제
// - JPG 는 항상 다시 저장해서 촬영 정보(EXIF: 촬영 위치 GPS·기종 등)를 지운다.
//   휴대폰 사진의 「세로로 찍음」(EXIF Orientation)은 지우기 전에 픽셀에 반영한다 (지우기만 하면 옆으로 누워 보인다)
// - PNG 는 클 때만 줄인다. GIF(움직이는 그림)·WEBP 는 그대로
// - 해상도가 터무니없이 큰 파일(작은 용량으로 거대한 그림을 만드는 파일)은 그림을 펼치기 전에 크기만 읽고 거부한다
final class ImageResizer {
	static final int MAX_EDGE = 1280;
	static final long MAX_PIXELS = 50_000_000L; // 약 8000×6000. 이보다 크면 받지 않는다
	private static final float JPEG_QUALITY = 0.85f;

	private ImageResizer() {
	}

	// 저장할 내용. 바꿀 필요가 없으면 원본 그대로 돌려준다
	static byte[] process(byte[] original, String extension) throws IOException {
		if (!"jpg".equals(extension) && !"png".equals(extension)) {
			return original;
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return original;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if ((long) width * height > MAX_PIXELS) {
					throw new InvalidPostException("画像の解像度が大きすぎます。(" + MAX_PIXELS / 1_000_000 + "00万画素まで)");
				}
				boolean jpeg = "jpg".equals(extension);
				if (!jpeg && Math.max(width, height) <= MAX_EDGE) {
					return original; // 작은 PNG 는 그대로
				}
				// 아주 큰 그림은 읽을 때부터 건너뛰며 읽어 메모리를 아낀다 (목표 크기의 2배 이상은 남긴다)
				ImageReadParam param = reader.getDefaultReadParam();
				int step = Math.max(1, Math.max(width, height) / (MAX_EDGE * 2));
				param.setSourceSubsampling(step, step, 0, 0);
				BufferedImage image = reader.read(0, param);
				if (jpeg) {
					image = applyOrientation(image, exifOrientation(original));
				}
				image = scaleDown(image, jpeg);
				return jpeg ? writeJpeg(image) : writePng(image);
			} finally {
				reader.dispose();
			}
		} catch (InvalidPostException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			// ImageIO 가 읽지 못하는 그림 (CMYK JPG, 일부가 깨진 파일 등): 원본 그대로 둔다 (형식은 앞에서 확인했다)
			return original;
		}
	}

	// 긴 변을 MAX_EDGE 이하로. 한 번에 크게 줄이면 거칠어지므로 반씩 줄인 뒤 마지막에 맞춘다
	private static BufferedImage scaleDown(BufferedImage image, boolean opaque) {
		int type = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		int longEdge = Math.max(image.getWidth(), image.getHeight());
		if (longEdge <= MAX_EDGE) {
			return opaque ? redraw(image, image.getWidth(), image.getHeight(), type) : image;
		}
		double scale = (double) MAX_EDGE / longEdge;
		int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		BufferedImage current = image;
		while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
			current = redraw(current, current.getWidth() / 2, current.getHeight() / 2, type);
		}
		return redraw(current, targetWidth, targetHeight, type);
	}

	private static BufferedImage redraw(BufferedImage source, int width, int height, int type) {
		BufferedImage target = new BufferedImage(width, height, type);
		Graphics2D g = target.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, 0, 0, width, height, null);
		} finally {
			g.dispose();
		}
		return target;
	}

	// EXIF Orientation (1: 그대로, 3: 180°, 6: 오른쪽 90°, 8: 왼쪽 90°, 2·4·5·7: 좌우 뒤집기 포함)
	private static BufferedImage applyOrientation(BufferedImage image, int orientation) {
		if (orientation <= 1 || orientation > 8) {
			return image;
		}
		int w = image.getWidth();
		int h = image.getHeight();
		boolean swap = orientation >= 5;
		AffineTransform t = new AffineTransform();
		switch (orientation) {
			case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
			case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
			case 4 -> { t.translate(0, h); t.scale(1, -1); }
			case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
			case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
			case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
			case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
			default -> { }
		}
		BufferedImage rotated = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rotated.createGraphics();
		try {
			g.drawImage(image, t, null);
		} finally {
			g.dispose();
		}
		return rotated;
	}

	// JPG 의 EXIF(APP1) 에서 Orientation(0x0112) 값을 읽는다. 없으면 1
	static int exifOrientation(byte[] jpeg) {
		int pos = 2; // SOI 다음
		while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
			int marker = jpeg[pos + 1] & 0xFF;
			int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
			if (marker == 0xDA || length < 2) {
				break; // 이미지 데이터 시작: EXIF 없음
			}
			int segment = pos + 4;
			if (marker == 0xE1 && segment + 14 <= jpeg.length && new String(jpeg, segment, 4, StandardCharsets.US_ASCII).equals("Exif")) {
				int tiff = segment + 6;
				boolean little = jpeg[tiff] == 'I';
				int ifd = tiff + readInt(jpeg, tiff + 4, little);
				if (ifd < tiff || ifd + 2 > jpeg.length) { // 값이 깨져 있으면 회전 정보 없음으로
					return 1;
				}
				int entries = readShort(jpeg, ifd, little);
				for (int i = 0; i < entries; i++) {
					int entry = ifd + 2 + i * 12;
					if (entry + 12 > jpeg.length) {
						break;
					}
					if (readShort(jpeg, entry, little) == 0x0112) {
						return readShort(jpeg, entry + 8, little);
					}
				}
				return 1;
			}
			pos += 2 + length;
		}
		return 1;
	}

	private static int readShort(byte[] b, int at, boolean little) {
		return little ? (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) : ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
	}

	private static int readInt(byte[] b, int at, boolean little) {
		return little
			? (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24)
			: ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
	}

	private static byte[] writeJpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(output);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(image, null, null), param); // 메타데이터 없이 (EXIF 를 쓰지 않는다)
		} finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private static byte[] writePng(BufferedImage image) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return bytes.toByteArray();
	}
}
