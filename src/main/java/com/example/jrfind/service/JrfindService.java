package com.example.jrfind.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile; // 파일 처리를 위한 import

import com.example.jrfind.domain.Category;
import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.FoundItemForm;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.Station;
import com.example.jrfind.repository.JrfindRepository;

@Service
public class JrfindService {
	// 한 페이지당 보여줄 게시글 수
	public static final int PAGE_SIZE = 15;

	// 비밀번호 확인 결과
	public enum PasswordCheck { OK, MISMATCH, LOCKED }

	private final JrfindRepository repository;
	private final ImageStorage imageStorage;
	private final PasswordAttemptLimiter attemptLimiter;
	// 비밀번호는 평문이 아니라 BCrypt 해시로 저장한다 (SecurityConfig 의 Bean)
	private final PasswordEncoder passwordEncoder;

	public JrfindService(JrfindRepository repository, ImageStorage imageStorage, PasswordAttemptLimiter attemptLimiter,
			PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.imageStorage = imageStorage;
        this.attemptLimiter = attemptLimiter;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Category> getCategories() {
        return repository.findAllCategories();
    }

    public List<String> getLines() {
        return repository.findAllLines();
    }

    public List<Station> getStations() {
        return repository.findAllStations();
    }

    // 게시글 목록 조회 (필터, 검색어, 정렬, 페이징 적용)
    public List<FoundItem> getItems(String line, String station, String category, String keyword, String sort, int page) {
        return repository.findAll(line, station, category, keyword, sort, page, PAGE_SIZE);
    }

    // 전체 게시글 개수 조회 (페이징 계산용)
    public int getTotalCount(String line, String station, String category, String keyword) {
        return repository.count(line, station, category, keyword);
    }

    // 회원이 쓴 글 (마이페이지)
    public List<FoundItem> getItemsByMember(Long memberId, int page, int size) {
        return repository.findByMember(memberId, page, size);
    }

    public int countItemsByMember(Long memberId) {
        return repository.countByMember(memberId);
    }

    // 검색어에 맞는 최신 글 limit 건 (통합 검색 화면용)
    public List<FoundItem> searchLatest(String keyword, int limit) {
        return repository.findAll(null, null, null, keyword, "latest", 1, limit);
    }

    // 게시글 저장 로직 (파일 처리 포함)
    // 회원(loginMember != null): 닉네임으로 작성, 비밀번호 없음 / 비회원: 4자리 비밀번호 + 匿名の天使 이름
    public void saveItem(FoundItemForm form, String password, LoginMember loginMember, MultipartFile imageFile) throws IOException {
        validateMasterData(form);

        FoundItem item = new FoundItem();
        applyForm(item, form);
        if (loginMember != null) {
            item.setMemberId(loginMember.getId());
            item.setAuthor(loginMember.getNickname());
        } else {
            item.setPassword(passwordEncoder.encode(password));
            item.setAuthor(generateAnonymousAuthor());
        }
        // 이미지가 없으면 null (사용자가 보낸 imgPath 값은 받지 않는다)
        item.setImgPath(imageStorage.store(imageFile));
        item.setRegistrationDate(LocalDateTime.now());
        repository.save(item);
    }

    // 게시글 수정 로직
    public void updateItem(Long id, FoundItemForm form, MultipartFile file, boolean isImageDeleted) throws IOException {
        FoundItem oldItem = repository.findById(id);
        if (oldItem == null) throw new IllegalArgumentException("해당 게시글이 존재하지 않습니다.");

        validateMasterData(form);
        applyForm(oldItem, form);

        // 이미지 처리
        if (isImageDeleted) {
            oldItem.setImgPath(null);
        } else if (file != null && !file.isEmpty()) {
            oldItem.setImgPath(imageStorage.store(file)); // 교체
        }

        oldItem.setModifiedDate(LocalDateTime.now());
        repository.update(oldItem);
    }

    // 입력값을 게시글에 반영. 제목은 클라이언트 값이 아니라 서버에서 만든다.
    private void applyForm(FoundItem item, FoundItemForm form) {
        item.setFoundDate(form.getFoundDate());
        item.setLineName(form.getLineName());
        item.setStationName(form.getStationName());
        item.setCategoryName(form.getCategoryName());
        item.setItemName(form.getItemName().trim());
        item.setContents(form.getContents());
        // 예: [2026-01-15][山手線][新宿][財布] 黄色い財布
        item.setTitle(String.format("[%s][%s][%s][%s] %s",
                item.getFoundDate(), item.getLineName(), item.getStationName(), item.getCategoryName(), item.getItemName()));
    }

    // 드롭다운에 없는 노선·역·카테고리를 직접 보내는 요청 차단
    private void validateMasterData(FoundItemForm form) {
        if (!repository.existsStation(form.getLineName(), form.getStationName())) {
            throw new InvalidPostException("発見した場所(路線・駅)を正しく選択してください。");
        }
        if (!repository.existsCategory(form.getCategoryName())) {
            throw new InvalidPostException("カテゴリーを正しく選択してください。");
        }
    }

    // 닉네임 생성 헬퍼 메서드
    private String generateAnonymousAuthor() {
        // 1. DB에 있는 모든 '匿名の天使' 닉네임을 가져옵니다.
        List<String> existingAuthors = repository.findAllAuthors();
        Set<Integer> usedNumbers = new HashSet<>();

        // 2. 현재 사용 중인 번호들만 추출해서 Set에 담습니다. (예: "匿名の天使 022" -> 22)
        for (String auth : existingAuthors) {
            try {
                String numPart = auth.replace("匿名の天使 ", "").trim();
                usedNumbers.add(Integer.parseInt(numPart));
            } catch (NumberFormatException e) {
                continue; // 번호 형식이 아니면 무시
            }
        }

        // 3. 경우의 수 1000개가 다 찼는지 확인
        if (usedNumbers.size() >= 1000) {
            // 다 찼다면 가장 오래된 작성자명을 재사용 (DB 조회)
            String oldest = repository.findOldestAuthor();
            return (oldest != null) ? oldest : "匿名の天使 000"; // 만약의 경우 대비
        }

        // 4. 빈 번호가 있다면 랜덤으로 뽑아서 사용 안 된 번호 찾기
        Random random = new Random();
        int newNum;
        do {
            newNum = random.nextInt(1000); // 0 ~ 999
        } while (usedNumbers.contains(newNum)); // 이미 있는 번호면 다시 뽑기

        // 5. 최종 포맷팅 (예: 22 -> "022")
        return String.format("匿名の天使 %03d", newNum);
    }

    // 게시글 상세 조회 (삭제됐거나 없으면 null)
    public FoundItem getItem(Long id) {
        return repository.findById(id);
    }

    // 비밀번호 확인 (clientKey: 시도 횟수를 세는 단위. 게시글 ID + 접속 IP)
    public PasswordCheck checkPassword(Long id, String password, String clientKey) {
        if (attemptLimiter.isLocked(clientKey)) {
            return PasswordCheck.LOCKED;
        }

        String hash = repository.findPasswordHash(id);
        if (hash != null && password != null && passwordEncoder.matches(password, hash)) {
            attemptLimiter.reset(clientKey);
            return PasswordCheck.OK;
        }

        attemptLimiter.recordFailure(clientKey);
        return PasswordCheck.MISMATCH;
    }

    // 삭제
    public void deleteItem(Long id) {
        repository.delete(id);
    }
}
