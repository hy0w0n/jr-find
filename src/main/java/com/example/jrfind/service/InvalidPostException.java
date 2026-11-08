package com.example.jrfind.service;

// 사용자 입력이 잘못되었을 때 (화면에 그대로 보여줄 메시지를 담는다)
public class InvalidPostException extends RuntimeException {
	public InvalidPostException(String message) {
		super(message);
	}
}
