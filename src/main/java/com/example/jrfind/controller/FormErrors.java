package com.example.jrfind.controller;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;

// 입력 검증 오류 중 첫 번째 메시지 (화면에서 모달로 보여줌)
final class FormErrors {
	private FormErrors() {
	}

	static String firstMessage(BindingResult bindingResult) {
		ObjectError error = bindingResult.getAllErrors().get(0);
		// 날짜에 문자를 넣는 등 형 변환 실패
		if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
			return "入力形式が正しくありません。";
		}
		return error.getDefaultMessage();
	}

	// 입력 오류로 화면을 다시 보여줄 때, 고른 사진은 다시 채울 수 없다 (브라우저가 파일 칸을 비운다) → 다시 고르도록 안내
	static String withImageNotice(String message, MultipartFile file) {
		return (file != null && !file.isEmpty()) ? message + "\n(選択した画像は、もう一度選択してください。)" : message;
	}
}
