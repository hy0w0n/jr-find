package com.example.jrfind.repository;

import java.util.List;
import java.util.Map;

import com.example.jrfind.domain.Category;
import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.Station;
import com.example.jrfind.domain.StationLocation;

public interface JrfindRepository {
	List<Category> findAllCategories();
	List<String> findAllLines();
	List<Station> findAllStations();
	List<StationLocation> findAllStationLocations();
	// 삭제되지 않은 비회원 글의 작성자명 (匿名の天使 번호를 고를 때)
	List<String> findAllAuthors();

	// 등록 화면에서 보낸 노선·역·카테고리가 실제로 있는 값인지 확인
	boolean existsStation(String line, String station);
	boolean existsLine(String line);
	boolean existsCategory(String categoryName);

	void save(FoundItem item);

	// 수정·삭제는 삭제되지 않은 글이고 사진이 읽었을 때(expectedImgPath)와 같을 때만 반영한다 (반영했으면 true)
	// 같은 글을 동시에 수정·삭제하면 한쪽만 반영되므로, 지운 글이나 바뀐 글에 사진 파일이 남지 않는다
	boolean update(FoundItem item, String expectedImgPath);

	// 조건에 맞는 게시글 조회 (필터, 검색어, 정렬, 페이징)
	List<FoundItem> findAll(String line, String station, String category, String keyword, String sort, int page, int size);

	// 조건에 맞는 게시글 전체 개수 (페이지 계산용)
	int count(String line, String station, String category, String keyword);

	// 회원이 쓴 게시글 (마이페이지, 최신 순)
	List<FoundItem> findByMember(Long memberId, int page, int size);

	int countByMember(Long memberId);

	// 역 이름 → 삭제되지 않은 게시글 수 (駅マップ)
	Map<String, Integer> countByStation();

	// 삭제되지 않은 게시글 1건 (없으면 null)
	FoundItem findById(Long id);

	// 삭제되지 않은 게시글의 비밀번호 해시 (없으면 null)
	String findPasswordHash(Long id);

	boolean delete(Long id, String expectedImgPath);

	// 게시글이 쓰고 있는 사진 파일명 (쓰지 않는 파일 정리용)
	List<String> findImagePaths();
}
