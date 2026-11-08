package com.example.jrfind.repository;

import java.util.List;
import java.util.Map;

import com.example.jrfind.domain.LostItem;

// 探しています 게시판 게시글
public interface LostItemRepository {
	void save(LostItem item);

	void update(LostItem item);

	// 조건에 맞는 게시글 조회 (필터, 검색어, 정렬, 페이징)
	List<LostItem> findAll(String line, String station, String category, String keyword, String sort, int page, int size);

	// 조건에 맞는 게시글 전체 개수 (페이지 계산용)
	int count(String line, String station, String category, String keyword);

	// 회원이 쓴 게시글 (마이페이지, 최신 순)
	List<LostItem> findByMember(Long memberId, int page, int size);

	int countByMember(Long memberId);

	// 역 이름 → 삭제되지 않은 게시글 수 (駅マップ, 역을 모르는 글은 제외)
	Map<String, Integer> countByStation();

	// 삭제되지 않은 게시글 1건 (없으면 null)
	LostItem findById(Long id);

	void updateResolved(Long id, boolean resolved);

	void delete(Long id);
}
