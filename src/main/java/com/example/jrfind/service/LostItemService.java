package com.example.jrfind.service;

import java.io.IOException;
import java.time.LocalDateTime;
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
		applyForm(item, form);

		String oldImage = item.getImgPath();
		if (isImageDeleted) {
			item.setImgPath(null);
		} else if (file != null && !file.isEmpty()) {
			item.setImgPath(imageStorage.store(file)); // 교체
		}

		item.setModifiedDate(LocalDateTime.now());
		repository.update(item);
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
	public void deleteItem(Long id) {
		LostItem item = repository.findById(id);
		repository.delete(id);
		if (item != null) {
			imageStorage.delete(item.getImgPath());
		}
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

	// 드롭다운에 없는 노선·역·카테고리를 직접 보내는 요청 차단
	private void validateMasterData(LostItemForm form) {
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
