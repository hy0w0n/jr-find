package com.example.jrfind.repository;

import java.util.List;
import java.util.Map;

import com.example.jrfind.domain.LostItem;

// 探しています 게시판 게시글
public interface LostItemRepository {
	void save(LostItem item);

	// 수정·삭제는 삭제되지 않은 글이고 사진이 읽었을 때(expectedImgPath)와 같을 때만 반영한다 (반영했으면 true)
	// 같은 글을 동시에 수정·삭제하면 한쪽만 반영되므로, 지운 글이나 바뀐 글에 사진 파일이 남지 않는다
	boolean update(LostItem item, String expectedImgPath);

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

	boolean delete(Long id, String expectedImgPath);

	// 게시글이 쓰고 있는 사진 파일명 (쓰지 않는 파일 정리용)
	List<String> findImagePaths();
}
