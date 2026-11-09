package com.example.jrfind.controller;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;

// 입력 검증 오류 중 첫 번째 메시지 (화면에서 모달로 보여줌)
final class FormErrors {
	private FormErrors() {
	}

	// 여러 항목이 틀렸으면 폼의 위쪽 항목(폼 클래스에 선언한 순서)부터 알려준다.
	// getAllErrors() 의 순서는 정해져 있지 않아서 같은 입력에도 요청마다 다른 항목의 메시지가 나왔다.
	static String firstMessage(BindingResult bindingResult) {
		List<String> fieldOrder = Arrays.stream(bindingResult.getTarget().getClass().getDeclaredFields()).map(Field::getName).toList();
		ObjectError error = bindingResult.getAllErrors().stream()
			.min(Comparator.comparingInt(e -> order(e, fieldOrder)))
			.orElseThrow();
		// 날짜에 문자를 넣는 등 형 변환 실패
		if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
			return "入力形式が正しくありません。";
		}
		return error.getDefaultMessage();
	}

	// 항목과 관계없는 오류가 먼저, 항목 오류는 선언 순서대로
	private static int order(ObjectError error, List<String> fieldOrder) {
		if (!(error instanceof FieldError fieldError)) {
			return -1;
		}
		int index = fieldOrder.indexOf(fieldError.getField());
		return (index < 0) ? Integer.MAX_VALUE : index;
	}

	// 보내지 못한 입력(FormDraft)을 폼에 다시 채웠을 때의 안내
	static final String DRAFT_RESTORED = "入力していた内容を元に戻しました。内容を確認して、もう一度送信してください。";

	// 입력 오류로 화면을 다시 보여줄 때, 고른 사진은 다시 채울 수 없다 (브라우저가 파일 칸을 비운다) → 다시 고르도록 안내
	static String withImageNotice(String message, MultipartFile file) {
		return withImageNotice(message, file != null && !file.isEmpty());
	}

	static String withImageNotice(String message, boolean imageDropped) {
		return imageDropped ? message + "\n(選択した画像は、もう一度選択してください。)" : message;
	}
}
