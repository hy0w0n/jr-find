package com.example.jrfind.repository;

import com.example.jrfind.domain.Member;

public interface MemberRepository {
	void save(Member member);

	// 로그인 ID로 회원 조회 (없으면 null)
	Member findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);
}
