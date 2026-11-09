package com.example.jrfind.controller;

import java.io.IOException; // 예외 처리를 위한 import
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping; // PostMapping 추가
import org.springframework.web.bind.annotation.RequestParam; // 파라미터 받기
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile; // 파일 받기
import org.springframework.web.server.ResponseStatusException;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.FoundItem; // 도메인 객체
import com.example.jrfind.domain.FoundItemForm;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.repository.KeywordCondition;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.InvalidPostException;
import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.JrfindService.PasswordCheck;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
public class JrfindController {
	private final JrfindService service;
	// 비밀번호 확인을 통과한 비회원 게시글 (세션별)
	private final VerifiedItems verifiedItems;
	private final CommentService commentService;

	public JrfindController(JrfindService service, VerifiedItems verifiedItems, CommentService commentService) {
		this.service = service;
		this.verifiedItems = verifiedItems;
		this.commentService = commentService;
	}

    // 1. 홈 화면 연결: http://localhost:8080/
    @GetMapping("/")
    public String home() {
        return "home"; // templates/home.html을 호출합니다.
    }

    // 2. 게시판 목록 화면 연결: http://localhost:8080/items
    @GetMapping("/items")
    public String list(
            Model model,
            @RequestParam(name = "line", required = false) String line,
            @RequestParam(name = "station", required = false) String station,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "q", required = false) String keyword, // 검색어 (제목·본문)
            @RequestParam(name = "sort", required = false, defaultValue = "latest") String sort,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page) {

        keyword = SearchKeyword.normalize(keyword);

        // 1. 페이징 계산 로직
        int totalItems = service.getTotalCount(line, station, category, keyword);
        int totalPages = (int) Math.ceil((double) totalItems / JrfindService.PAGE_SIZE);
        // page=0, 음수, 마지막 페이지보다 큰 값이 와도 1 ~ totalPages 범위로 맞춘다
        int currentPage = Math.max(1, Math.min(page, totalPages));

        // 2. 게시글 목록 데이터 (필터, 정렬, 페이징 적용)
        List<FoundItem> items = service.getItems(line, station, category, keyword, sort, currentPage);
        model.addAttribute("items", items);
        model.addAttribute("totalItems", totalItems);

        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);

        // 3. 필터 유지 및 드롭다운 데이터
        // 사용자가 선택한 필터값을 화면에 그대로 다시 돌려줘서(selected 상태 유지) UX를 높입니다.
        model.addAttribute("currentLine", line);
        model.addAttribute("currentStation", station);
        model.addAttribute("currentCategory", category);
        model.addAttribute("currentKeyword", keyword);
        model.addAttribute("keywordTruncated", KeywordCondition.isTruncated(keyword)); // 단어가 너무 많아 뒤는 쓰지 않음
        model.addAttribute("currentSort", sort);

        model.addAttribute("lines", service.getLines());
        model.addAttribute("stations", service.getStations());
        model.addAttribute("categories", service.getCategories());

        return "ItemList";
    }

    //3. 게시글 등록 화면 연결: http://localhost:8080/items/new
    @GetMapping("/items/new")
    public String registerForm(Model model) {
        return showRegisterForm(model, null);
    }

    @PostMapping("/items/new")
    public String createItem(
            @Valid @ModelAttribute("form") FoundItemForm form,
            BindingResult bindingResult,
            @RequestParam(name = "password", required = false) String password, // 비회원만 사용
            @RequestParam(name = "imageFile", required = false) MultipartFile file,
            @AuthenticationPrincipal LoginMember loginMember,
            Model model) {
        // 서버 측 입력 검증 (화면 JS 검증을 거치지 않고 직접 보낸 요청 대비)
        // 오류로 다시 보여줄 때 입력한 값은 form 으로 다시 채우고, 고른 사진은 다시 고르도록 안내한다
        if (bindingResult.hasErrors()) {
            return showRegisterForm(model, FormErrors.withImageNotice(FormErrors.firstMessage(bindingResult), file));
        }
        // 비회원은 수정·삭제용 4자리 비밀번호 필수
        if (loginMember == null && (password == null || !password.matches("\\d{4}"))) {
            return showRegisterForm(model, FormErrors.withImageNotice("パスワードは半角数字4桁で入力してください。", file));
        }

        try {
            // 서비스에게 데이터와 파일을 넘겨서 저장하라고 시킴
            service.saveItem(form, password, loginMember, file);
        } catch (InvalidPostException e) {
            return showRegisterForm(model, FormErrors.withImageNotice(e.getMessage(), file));
        } catch (IOException e) {
            e.printStackTrace(); // 파일 저장 실패 시 에러 로그 출력
            return showRegisterForm(model, FormErrors.withImageNotice("画像の保存に失敗しました。", file));
        }

        // 저장이 끝나면 목록 화면으로 리다이렉트 (새로고침 중복 등록 방지)
        return "redirect:/items";
    }

    // 상세 페이지 연결: http://localhost:8080/items/1
    @GetMapping("/items/{id}")
    public String detail(@PathVariable("id") Long id, @AuthenticationPrincipal LoginMember loginMember, Model model) {
        FoundItem item = findItemOrThrow(id);
        model.addAttribute("item", item);
        // 회원 글이면 작성자 본인에게만 수정·삭제 버튼을 보여준다
        model.addAttribute("isOwner", isOwner(item, loginMember));
        model.addAttribute("comments", commentService.getComments(Board.FOUND, id));
        return "itemDetail"; // templates/itemDetail.html 호출
    }

    // 비밀번호 확인 API (AJAX용, 비회원 글 전용)
    // 맞으면 세션에 "확인 완료"로 기록해 두고, 이후 수정·삭제 요청에서 서버가 이 기록을 확인한다.
    @PostMapping("/items/{id}/check-password")
    @ResponseBody
    public ResponseEntity<Boolean> checkPassword(
            @PathVariable("id") Long id,
            @RequestParam("password") String password,
            HttpServletRequest request) {
        PasswordCheck result = service.checkPassword(id, password, id + "@" + request.getRemoteAddr());

        if (result == PasswordCheck.LOCKED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(false);
        }
        if (result == PasswordCheck.OK) {
            verifiedItems.add(id);
        }
        return ResponseEntity.ok(result == PasswordCheck.OK);
    }

    // 삭제 처리
    @PostMapping("/items/{id}/delete")
    public String deleteItem(@PathVariable("id") Long id, @AuthenticationPrincipal LoginMember loginMember) {
        FoundItem item = findItemOrThrow(id);
        requireModifiable(item, loginMember);

        service.deleteItem(id);
        verifiedItems.remove(id);
        return "redirect:/items"; // 삭제 후 목록으로 이동
    }

    // 게시글 수정 화면 연결: http://localhost:8080/items/{id}/edit
    @GetMapping("/items/{id}/edit")
    public String editForm(@PathVariable("id") Long id, @AuthenticationPrincipal LoginMember loginMember, Model model) {
        FoundItem item = findItemOrThrow(id);

        // 권한 없이 URL로 직접 들어오면 상세 화면으로 돌려보낸다
        if (!canModify(item, loginMember)) {
            return "redirect:/items/" + id;
        }
        return showEditForm(model, item, null);
    }

    // 수정 처리
    @PostMapping("/items/{id}/edit")
    public String updateItem(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("form") FoundItemForm form,
            BindingResult bindingResult,
            @RequestParam(name = "imageFile", required = false) MultipartFile file,
            @RequestParam(value = "isImageDeleted", defaultValue = "false") boolean isImageDeleted,
            @AuthenticationPrincipal LoginMember loginMember,
            Model model) {
        FoundItem item = findItemOrThrow(id);
        requireModifiable(item, loginMember);

        if (bindingResult.hasErrors()) {
            return showEditFormError(model, item, form, FormErrors.firstMessage(bindingResult), file, isImageDeleted);
        }

        try {
            service.updateItem(id, form, file, isImageDeleted);
        } catch (InvalidPostException e) {
            return showEditFormError(model, item, form, e.getMessage(), file, isImageDeleted);
        } catch (IOException e) {
            e.printStackTrace();
            return showEditFormError(model, item, form, "画像の保存に失敗しました。", file, isImageDeleted);
        }

        verifiedItems.remove(id); // 비회원 글은 다시 수정하려면 비밀번호를 다시 확인
        return "redirect:/items/" + id; // 수정 완료 후 상세 페이지로 이동
    }

    // 등록 화면 표시 (errorMessage 가 있으면 화면에서 모달로 보여줌)
    private String showRegisterForm(Model model, String errorMessage) {
        model.addAttribute("categories", service.getCategories());
        model.addAttribute("lines", service.getLines());
        model.addAttribute("stations", service.getStations());
        model.addAttribute("errorMessage", errorMessage);
        return "registerForm";
    }

    // 수정 화면 표시 (기존 데이터 + 드롭다운 메뉴 데이터)
    private String showEditForm(Model model, FoundItem item, String errorMessage) {
        model.addAttribute("item", item);
        model.addAttribute("categories", service.getCategories());
        model.addAttribute("lines", service.getLines());
        model.addAttribute("stations", service.getStations());
        model.addAttribute("errorMessage", errorMessage);
        return "editForm";
    }

    // 입력 오류로 수정 화면을 다시 보여줄 때
    // - 수정 화면은 item 의 값을 보여주므로, DB 값이 아니라 입력한 값으로 바꿔서 보여준다 (DB 는 그대로)
    // - 지운 사진은 지운 상태로 보여준다 (되살아난 채로 저장되지 않게), 고른 사진은 다시 고르도록 안내
    private String showEditFormError(Model model, FoundItem item, FoundItemForm form, String errorMessage,
            MultipartFile file, boolean isImageDeleted) {
        item.setFoundDate(form.getFoundDate());
        item.setLineName(form.getLineName());
        item.setStationName(form.getStationName());
        item.setCategoryName(form.getCategoryName());
        item.setItemName(form.getItemName());
        item.setContents(form.getContents());
        if (isImageDeleted) {
            item.setImgPath(null);
            model.addAttribute("isImageDeleted", true);
        }
        return showEditForm(model, item, FormErrors.withImageNotice(errorMessage, file));
    }

    // 없는 글·삭제된 글이면 404 화면
    private FoundItem findItemOrThrow(Long id) {
        FoundItem item = service.getItem(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return item;
    }

    // 회원 글을 쓴 본인인지
    private boolean isOwner(FoundItem item, LoginMember loginMember) {
        return item.getMemberId() != null && loginMember != null && item.getMemberId().equals(loginMember.getId());
    }

    // 수정·삭제 권한: 회원 글은 작성자 본인, 비회원 글은 4자리 비밀번호 확인을 통과한 세션
    private boolean canModify(FoundItem item, LoginMember loginMember) {
        if (item.getMemberId() != null) {
            return isOwner(item, loginMember);
        }
        return verifiedItems.contains(item.getId());
    }

    // 권한 없는 수정·삭제 요청은 403
    private void requireModifiable(FoundItem item, LoginMember loginMember) {
        if (!canModify(item, loginMember)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
