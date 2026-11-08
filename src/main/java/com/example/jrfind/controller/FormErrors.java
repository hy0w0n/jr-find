package com.example.jrfind.controller;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

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
}
