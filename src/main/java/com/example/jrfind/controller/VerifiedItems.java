package com.example.jrfind.controller;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

// 비밀번호 확인을 통과한 게시글 ID를 세션(접속자별)에 보관한다.
// 수정·삭제 요청이 들어오면 여기에 ID가 있는지 서버에서 확인한다.
@Component
@SessionScope
public class VerifiedItems implements Serializable {
	private final Set<Long> itemIds = ConcurrentHashMap.newKeySet();

	public void add(Long id) {
		itemIds.add(id);
	}

	public boolean contains(Long id) {
		return itemIds.contains(id);
	}

	public void remove(Long id) {
		itemIds.remove(id);
	}
}
