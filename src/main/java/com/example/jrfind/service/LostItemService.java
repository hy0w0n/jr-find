package com.example.jrfind.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.LostItemForm;
import com.example.jrfind.repository.JrfindRepository;
import com.example.jrfind.repository.LostItemRepository;

// 探しています 게시판 (회원만 작성, 작성자 본인만 수정·삭제)
@Service
public class LostItemService {
	public static final int PAGE_SIZE = 15;
	// 잃어버린 날·발견한 날의 하한 (두 게시판 공통. 0001-01-01 같은 입력 실수 방지)
	public static final LocalDate MIN_POST_DATE = LocalDate.of(2000, 1, 1);

	private final LostItemRepository repository;
	// 노선·역·카테고리 존재 여부 확인용 (見つけた 게시판과 같은 데이터 사용)
	private final JrfindRepository masterRepository;
	private final ImageStorage imageStorage;

	public LostItemService(LostItemRepository repository, JrfindRepository masterRepository, ImageStorage imageStorage) {
		this.repository = repository;
		this.masterRepository = masterRepository;
		this.imageStorage = imageStorage;
	}

	public List<LostItem> getItems(String line, String station, String category, String keyword, String sort, int page) {
		return repository.findAll(line, station, category, keyword, sort, page, PAGE_SIZE);
	}

	public int getTotalCount(String line, String station, String category, String keyword) {
		return repository.count(line, station, category, keyword);
	}

	// 회원이 쓴 글 (마이페이지)
	public List<LostItem> getItemsByMember(Long memberId, int page, int size) {
		return repository.findByMember(memberId, page, size);
	}

	public int countItemsByMember(Long memberId) {
		return repository.countByMember(memberId);
	}

	// 검색어에 맞는 최신 글 limit 건 (통합 검색 화면용)
	public List<LostItem> searchLatest(String keyword, int limit) {
		return repository.findAll(null, null, null, keyword, "latest", 1, limit);
	}

	// 삭제됐거나 없으면 null
	public LostItem getItem(Long id) {
		return repository.findById(id);
	}

	public void saveItem(LostItemForm form, LoginMember loginMember, MultipartFile imageFile) throws IOException {
		validateMasterData(form);

		LostItem item = new LostItem();
		applyForm(item, form);
		item.setMemberId(loginMember.getId());
		item.setAuthor(loginMember.getNickname());
		item.setImgPath(imageStorage.store(imageFile));
		item.setRegistrationDate(LocalDateTime.now());
		repository.save(item);
	}

	public void updateItem(LostItem item, LostItemForm form, MultipartFile file, boolean isImageDeleted) throws IOException {
		validateMasterData(form);
		List<Object> before = contentOf(item);
		applyForm(item, form);

		String oldImage = item.getImgPath();
		String newImage = null;
		if (isImageDeleted) {
			item.setImgPath(null);
		} else if (file != null && !file.isEmpty()) {
			newImage = imageStorage.store(file); // 교체
			item.setImgPath(newImage);
		}

		// 아무것도 바꾸지 않고 저장했으면 그대로 둔다 (「編集済み」 표시를 붙이지 않음)
		if (before.equals(contentOf(item))) {
			return;
		}
		item.setModifiedDate(LocalDateTime.now());
		// 그사이 다른 요청이 글을 지웠거나 사진을 바꿨으면 반영하지 않고, 방금 저장한 사진 파일도 지운다
		if (!repository.update(item, oldImage)) {
			imageStorage.delete(newImage);
			throw new InvalidPostException(ConcurrentChange.message(repository.findById(item.getId()) == null));
		}
		// 바꾸거나 지운 사진의 이전 파일은 DB 에 반영한 뒤에 지운다
		if (!Objects.equals(oldImage, item.getImgPath())) {
			imageStorage.delete(oldImage);
		}
	}

	// 찾았음 표시 / 해제
	public void setResolved(Long id, boolean resolved) {
		repository.updateResolved(id, resolved);
	}

	// 게시글은 DEL_FLG 로 숨기고 사진 파일은 지운다 (삭제한 글의 사진이 /images/파일명 으로 계속 보이지 않도록)
	// 그사이 다른 요청이 사진을 바꿨으면 바뀐 사진을 다시 읽어서 지운다
	public void deleteItem(Long id) {
		for (int attempt = 0; attempt < ConcurrentChange.MAX_ATTEMPTS; attempt++) {
			LostItem item = repository.findById(id);
			if (item == null) {
				return; // 이미 삭제됨
			}
			if (repository.delete(id, item.getImgPath())) {
				imageStorage.delete(item.getImgPath());
				return;
			}
		}
		throw new IllegalStateException("게시글 삭제 중 사진이 계속 바뀌었습니다: " + id);
	}

	// 입력값을 게시글에 반영. 제목은 서버에서 만든다.
	private void applyForm(LostItem item, LostItemForm form) {
		item.setLostDate(form.getLostDate());
		item.setLineName(form.getLineName());
		item.setStationName(form.isStationUnknown() ? null : form.getStationName());
		item.setCategoryName(form.getCategoryName());
		item.setItemName(form.getItemName().trim());
		item.setContents(form.getContents());
		// 예: [2026-01-15][山手線][駅不明][財布] 黒い長財布
		String station = (item.getStationName() == null) ? "駅不明" : item.getStationName();
		item.setTitle(String.format("[%s][%s][%s][%s] %s",
				item.getLostDate(), item.getLineName(), station, item.getCategoryName(), item.getItemName()));
	}

	// 수정 전후 비교용 (사용자가 바꿀 수 있는 항목과 사진)
	private List<Object> contentOf(LostItem item) {
		return Arrays.asList(item.getLostDate(), item.getLineName(), item.getStationName(), item.getCategoryName(),
				item.getItemName(), item.getContents(), item.getImgPath());
	}

	// 드롭다운에 없는 노선·역·카테고리를 직접 보내는 요청 차단, 너무 오래된 날짜(입력 실수) 차단
	private void validateMasterData(LostItemForm form) {
		if (form.getLostDate().isBefore(MIN_POST_DATE)) {
			throw new InvalidPostException("なくした日は2000年1月1日以降の日付を入力してください。");
		}
		boolean placeExists = form.isStationUnknown()
				? masterRepository.existsLine(form.getLineName())
				: masterRepository.existsStation(form.getLineName(), form.getStationName());
		if (!placeExists) {
			throw new InvalidPostException("なくした場所(路線・駅)を正しく選択してください。");
		}
		if (!masterRepository.existsCategory(form.getCategoryName())) {
			throw new InvalidPostException("カテゴリーを正しく選択してください。");
		}
	}
}
