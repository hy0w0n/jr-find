package com.example.jrfind.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

	// 비밀번호 확인 결과 (NOT_FOUND: 없는 글·삭제된 글·회원 글이라 확인할 비밀번호가 없음)
	public enum PasswordCheck { OK, MISMATCH, LOCKED, NOT_FOUND }

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
        List<Object> before = contentOf(oldItem);
        applyForm(oldItem, form);

        // 이미지 처리
        String oldImage = oldItem.getImgPath();
        String newImage = null;
        if (isImageDeleted) {
            oldItem.setImgPath(null);
        } else if (file != null && !file.isEmpty()) {
            newImage = imageStorage.store(file); // 교체
            oldItem.setImgPath(newImage);
        }

        // 아무것도 바꾸지 않고 저장했으면 그대로 둔다 (「編集済み」 표시를 붙이지 않음)
        if (before.equals(contentOf(oldItem))) {
            return;
        }
        oldItem.setModifiedDate(LocalDateTime.now());
        // 그사이 다른 요청이 글을 지웠거나 사진을 바꿨으면 반영하지 않고, 방금 저장한 사진 파일도 지운다
        // (지운 글에 새 사진이 공개된 채 남거나, 어느 글에서도 쓰지 않는 파일이 쌓이지 않도록)
        if (!repository.update(oldItem, oldImage)) {
            imageStorage.delete(newImage);
            throw new InvalidPostException(ConcurrentChange.message(repository.findById(id) == null));
        }
        // 바꾸거나 지운 사진의 이전 파일은 DB 에 반영한 뒤에 지운다
        if (!Objects.equals(oldImage, oldItem.getImgPath())) {
            imageStorage.delete(oldImage);
        }
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

    // 수정 전후 비교용 (사용자가 바꿀 수 있는 항목과 사진)
    private List<Object> contentOf(FoundItem item) {
        return Arrays.asList(item.getFoundDate(), item.getLineName(), item.getStationName(), item.getCategoryName(),
                item.getItemName(), item.getContents(), item.getImgPath());
    }

    // 드롭다운에 없는 노선·역·카테고리를 직접 보내는 요청 차단, 너무 오래된 날짜(입력 실수) 차단
    private void validateMasterData(FoundItemForm form) {
        if (form.getFoundDate().isBefore(LostItemService.MIN_POST_DATE)) {
            throw new InvalidPostException("発見した日は2000年1月1日以降の日付を入力してください。");
        }
        if (!repository.existsStation(form.getLineName(), form.getStationName())) {
            throw new InvalidPostException("発見した場所(路線・駅)を正しく選択してください。");
        }
        if (!repository.existsCategory(form.getCategoryName())) {
            throw new InvalidPostException("カテゴリーを正しく選択してください。");
        }
    }

    // 닉네임 생성 헬퍼 메서드
    private String generateAnonymousAuthor() {
        // 1. DB에 있는 '匿名の天使' 닉네임을 가져옵니다. (삭제된 글의 번호는 다시 쓴다)
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

        // 3. 빈 번호가 있다면 랜덤으로 뽑아서 사용 안 된 번호 찾기
        // 1000개가 다 찼으면 아무 번호나 쓴다 (같은 이름의 글이 생긴다)
        // (예전에는 가장 오래된 글의 이름을 다시 썼는데, 그러면 그 뒤로 쓰는 비회원 글이 전부 같은 이름이 됐다)
        Random random = new Random();
        int newNum;
        do {
            newNum = random.nextInt(1000); // 0 ~ 999
        } while (usedNumbers.size() < 1000 && usedNumbers.contains(newNum)); // 이미 있는 번호면 다시 뽑기

        // 4. 최종 포맷팅 (예: 22 -> "022")
        return String.format("匿名の天使 %03d", newNum);
    }

    // 게시글 상세 조회 (삭제됐거나 없으면 null)
    public FoundItem getItem(Long id) {
        return repository.findById(id);
    }

    // 비밀번호 확인 (clientKey: 시도 횟수를 세는 단위. 게시글 ID + 접속 IP)
    public PasswordCheck checkPassword(Long id, String password, String clientKey) {
        // 확인할 비밀번호가 없는 글은 틀린 횟수로 세지 않는다
        // (다른 탭에서 지운 글에 맞는 비밀번호를 넣어도 「間違っています」가 나오고 잠기거나, 없는 글 번호로 기록이 쌓였다)
        String hash = repository.findPasswordHash(id);
        if (hash == null) {
            return PasswordCheck.NOT_FOUND;
        }
        if (attemptLimiter.isLocked(clientKey)) {
            return PasswordCheck.LOCKED;
        }
        if (password != null && passwordEncoder.matches(password, hash)) {
            attemptLimiter.reset(clientKey);
            return PasswordCheck.OK;
        }
        // 이번에 틀려서 잠기면 바로 잠겼다고 알린다 (다음 시도에서야 알리면 갑자기 막힌 것처럼 보인다)
        return attemptLimiter.recordFailure(clientKey) ? PasswordCheck.LOCKED : PasswordCheck.MISMATCH;
    }

    // 몇 번 더 틀리면 잠기는지 (화면 안내용)
    public int remainingPasswordAttempts(String clientKey) {
        return attemptLimiter.remainingAttempts(clientKey);
    }

    // 삭제: 게시글은 DEL_FLG 로 숨기고 사진 파일은 지운다 (삭제한 글의 사진이 /images/파일명 으로 계속 보이지 않도록)
    // 그사이 다른 요청이 사진을 바꿨으면 바뀐 사진을 다시 읽어서 지운다
    public void deleteItem(Long id) {
        for (int attempt = 0; attempt < ConcurrentChange.MAX_ATTEMPTS; attempt++) {
            FoundItem item = repository.findById(id);
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
}
