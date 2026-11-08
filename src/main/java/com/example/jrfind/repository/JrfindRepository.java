package com.example.jrfind.repository;

import java.util.List;

import com.example.jrfind.domain.Category;
import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.Station;

public interface JrfindRepository {
	List<Category> findAllCategories();
	List<String> findAllLines();
	List<Station> findAllStations();
	List<String> findAllAuthors();
	String findOldestAuthor();

	// 등록 화면에서 보낸 노선·역·카테고리가 실제로 있는 값인지 확인
	boolean existsStation(String line, String station);
	boolean existsLine(String line);
	boolean existsCategory(String categoryName);

	void save(FoundItem item);

	void update(FoundItem item);

	// 조건에 맞는 게시글 조회 (필터, 검색어, 정렬, 페이징)
	List<FoundItem> findAll(String line, String station, String category, String keyword, String sort, int page, int size);

	// 조건에 맞는 게시글 전체 개수 (페이지 계산용)
	int count(String line, String station, String category, String keyword);

	// 회원이 쓴 게시글 (마이페이지, 최신 순)
	List<FoundItem> findByMember(Long memberId, int page, int size);

	int countByMember(Long memberId);

	// 삭제되지 않은 게시글 1건 (없으면 null)
	FoundItem findById(Long id);

	// 삭제되지 않은 게시글의 비밀번호 해시 (없으면 null)
	String findPasswordHash(Long id);

	void delete(Long id);
}
