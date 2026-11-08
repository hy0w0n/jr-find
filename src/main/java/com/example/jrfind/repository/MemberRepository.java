package com.example.jrfind.repository;

import com.example.jrfind.domain.Member;

public interface MemberRepository {
	void save(Member member);

	// 로그인 ID로 회원 조회 (없으면 null)
	Member findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);

	// 없으면 null
	Member findById(Long id);

	// 氏名・ニックネーム・生年月日・電話番号 (ID·비밀번호는 바꾸지 않음)
	void updateProfile(Member member);

	void updatePassword(Long id, String passwordHash);

	// 게시글·댓글에 저장해 둔 작성자명(작성 시점의 닉네임)을 새 닉네임으로 바꾼다
	void updateAuthorName(Long memberId, String nickname);
}
