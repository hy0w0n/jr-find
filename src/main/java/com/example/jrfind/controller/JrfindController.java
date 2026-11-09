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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.FoundItem; // 도메인 객체
import com.example.jrfind.domain.FoundItemForm;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.repository.KeywordCondition;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.InvalidPostException;
import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.JrfindService.PasswordCheck;
import com.example.jrfind.service.PasswordAttemptLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
public class JrfindController {
	private final JrfindService service;
	// 비밀번호 확인을 통과한 비회원 게시글 (세션별)
	private final VerifiedItems verifiedItems;
	private final CommentService commentService;
	// 같은 내용을 연달아 두 번 등록해도 한 번만 저장 (세션별)
	private final SubmittedForms submittedForms;

	public JrfindController(JrfindService service, VerifiedItems verifiedItems, CommentService commentService, SubmittedForms submittedForms) {
		this.service = service;
		this.verifiedItems = verifiedItems;
		this.commentService = commentService;
		this.submittedForms = submittedForms;
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
        // 페이지 번호·정렬 머리글·「検索をやめる」 링크 (조건을 유지한 주소)
        model.addAttribute("query", new BoardQuery("/items", line, station, category, keyword, sort));
        model.addAttribute("pageNumbers", BoardQuery.pageNumbers(currentPage, totalPages));

        model.addAttribute("lines", service.getLines());
        model.addAttribute("stationLines", BoardQuery.stationLines(service.getStations()));
        model.addAttribute("categories", service.getCategories());

        return "ItemList";
    }

    //3. 게시글 등록 화면 연결: http://localhost:8080/items/new
    @GetMapping("/items/new")
    public String registerForm(HttpServletRequest request, Model model) {
        // 시간이 지나 보내지 못한 입력이 있으면 다시 채운다 (ExpiredFormHandler)
        FormDraft draft = FormDraft.take(request, request.getRequestURI());
        if (draft != null) {
            model.addAttribute("form", draft.bindTo(new FoundItemForm()));
            return showRegisterForm(model, FormErrors.withImageNotice(FormErrors.DRAFT_RESTORED, draft.imageDropped()));
        }
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

        // 같은 내용을 방금 보냈으면 다시 저장하지 않고, 처음 보냈을 때와 같이 목록으로
        String submissionKey = SubmittedForms.key("/items/new", form.getFoundDate(), form.getLineName(), form.getStationName(),
                form.getCategoryName(), form.getItemName(), form.getContents(), imageIdentity(file));
        if (!submittedForms.begin(submissionKey)) {
            return "redirect:/items";
        }
        try {
            // 서비스에게 데이터와 파일을 넘겨서 저장하라고 시킴
            service.saveItem(form, password, loginMember, file);
        } catch (InvalidPostException e) {
            submittedForms.cancel(submissionKey);
            return showRegisterForm(model, FormErrors.withImageNotice(e.getMessage(), file));
        } catch (IOException e) {
            e.printStackTrace(); // 파일 저장 실패 시 에러 로그 출력
            submittedForms.cancel(submissionKey);
            return showRegisterForm(model, FormErrors.withImageNotice("画像の保存に失敗しました。", file));
        }

        // 저장이 끝나면 목록 화면으로 리다이렉트 (새로고침 중복 등록 방지)
        return "redirect:/items";
    }

    // 상세 페이지 연결: http://localhost:8080/items/1
    @GetMapping("/items/{id}")
    public String detail(@PathVariable("id") Long id,
            @RequestParam(name = "verify", required = false) String verify,
            @RequestParam(name = "reverify", required = false) String reverify,
            @RequestParam(name = "confirmDelete", required = false) String confirmDelete,
            @AuthenticationPrincipal LoginMember loginMember, HttpServletRequest request, Model model) {
        FoundItem item = findItemOrThrow(id);
        model.addAttribute("item", item);
        // 회원 글이면 작성자 본인에게만 수정·삭제 버튼을 보여준다
        model.addAttribute("isOwner", isOwner(item, loginMember));
        model.addAttribute("comments", commentService.getComments(Board.FOUND, id));
        CommentController.restoreCommentDraft(Board.FOUND, id, loginMember, request, model);
        model.addAttribute("listUrl", BoardQuery.listUrlFrom(request, "/items")); // 「一覧」: 보던 목록으로
        // 비회원 글 수정을 보냈는데 비밀번호 확인이 끝나 있었으면, 비밀번호 창을 바로 열어서 다시 묻는다 (입력한 내용은 보관돼 있다)
        boolean guestPost = item.getMemberId() == null;
        boolean reverifying = reverify != null && guestPost && FormDraft.exists(request, editUrl(id));
        model.addAttribute("reverify", reverifying);
        // 비밀번호 확인 창·삭제 확인 창을 연 화면 (JS 없이 수정·삭제 버튼을 누른 경우. JS 가 있으면 화면 안에서 연다)
        String verifyAction = reverifying ? "edit" : (guestPost && ("edit".equals(verify) || "delete".equals(verify))) ? verify : null;
        model.addAttribute("verifyAction", verifyAction);
        model.addAttribute("confirmDelete", confirmDelete != null && canModify(item, loginMember));
        return "itemDetail"; // templates/itemDetail.html 호출
    }

    // 비밀번호 확인 결과 (화면의 JS 가 읽는다)
    // remaining: 몇 번 더 틀리면 잠기는지, lockMinutes: 잠기는 시간(분)
    public record PasswordCheckResponse(boolean ok, int remaining, long lockMinutes) {
    }

    // 비밀번호 확인 API (AJAX용, 비회원 글 전용)
    // 맞으면 세션에 "확인 완료"로 기록해 두고, 이후 수정·삭제 요청에서 서버가 이 기록을 확인한다.
    // 없는 글·삭제된 글·회원 글은 404, 연속으로 틀려서 잠기면 429
    @PostMapping("/items/{id}/check-password")
    @ResponseBody
    public ResponseEntity<PasswordCheckResponse> checkPassword(
            @PathVariable("id") Long id,
            @RequestParam("password") String password,
            HttpServletRequest request) {
        String clientKey = id + "@" + request.getRemoteAddr();
        PasswordCheck result = service.checkPassword(id, password, clientKey);
        long lockMinutes = PasswordAttemptLimiter.LOCK_TIME.toMinutes();

        if (result == PasswordCheck.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (result == PasswordCheck.LOCKED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new PasswordCheckResponse(false, 0, lockMinutes));
        }
        if (result == PasswordCheck.OK) {
            verifiedItems.add(id);
        }
        return ResponseEntity.ok(new PasswordCheckResponse(result == PasswordCheck.OK, service.remainingPasswordAttempts(clientKey), lockMinutes));
    }

    // 비밀번호 확인 (JS 를 쓰지 않는 폼 전송. 결과는 화면 이동으로)
    // 맞으면 수정 화면 / 삭제 확인 창을 연 상세 화면으로, 틀리면 비밀번호 창을 연 상세 화면으로 안내와 함께
    @PostMapping("/items/{id}/verify")
    public String verifyPassword(
            @PathVariable("id") Long id,
            @RequestParam(name = "action", defaultValue = "edit") String action,
            @RequestParam(name = "password", defaultValue = "") String password,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        boolean delete = "delete".equals(action);
        String clientKey = id + "@" + request.getRemoteAddr();
        PasswordCheck result = service.checkPassword(id, password, clientKey);
        if (result == PasswordCheck.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (result == PasswordCheck.OK) {
            verifiedItems.add(id);
            return delete ? "redirect:/items/" + id + "?confirmDelete" : "redirect:" + editUrl(id);
        }
        long lockMinutes = PasswordAttemptLimiter.LOCK_TIME.toMinutes();
        int remaining = service.remainingPasswordAttempts(clientKey);
        String message = (result == PasswordCheck.LOCKED)
                ? "入力回数の上限を超えました。\n" + lockMinutes + "分ほど経ってから再度お試しください。"
                : "パスワードが間違っています。\nもう一度入力してください。"
                    + ((remaining <= 3) ? "\n(あと" + remaining + "回間違えると、" + lockMinutes + "分間入力できなくなります。)" : "");
        redirectAttributes.addFlashAttribute("verifyError", message);
        return "redirect:/items/" + id + "?verify=" + (delete ? "delete" : "edit");
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
    public String editForm(@PathVariable("id") Long id, @RequestParam(name = "expired", required = false) String expired,
            @AuthenticationPrincipal LoginMember loginMember, HttpServletRequest request, Model model) {
        FoundItem item = findItemOrThrow(id);

        if (!canModify(item, loginMember)) {
            // 회원 글인데 로그인이 끝나 있으면 로그인 화면으로 (로그인하면 이 화면으로 돌아온다)
            if (item.getMemberId() != null && loginMember == null) {
                return "redirect:" + UriComponentsBuilder.fromPath("/login").queryParam("required").queryParam("next", editUrl(id)).build().toUriString();
            }
            // 비회원 글이고 보내지 못한 수정 내용이 있으면 비밀번호를 다시 묻는다
            if (item.getMemberId() == null && FormDraft.exists(request, editUrl(id))) {
                return "redirect:/items/" + id + "?reverify" + ((expired != null) ? "&expired" : "");
            }
            // 권한 없이 URL로 직접 들어오면 상세 화면으로 돌려보낸다 (시간이 지나 보내지 못했다는 안내는 그대로)
            return "redirect:/items/" + id + ((expired != null) ? "?expired" : "");
        }

        // 보내지 못한 수정 내용이 있으면 다시 채운다
        FormDraft draft = FormDraft.take(request, editUrl(id));
        if (draft != null) {
            FoundItemForm form = draft.bindTo(new FoundItemForm());
            return showEditFormError(model, item, form, FormErrors.withImageNotice(FormErrors.DRAFT_RESTORED, draft.imageDropped()),
                    null, Boolean.parseBoolean(draft.get("isImageDeleted")));
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
            HttpServletRequest request,
            Model model) {
        FoundItem item = findItemOrThrow(id);
        // 비회원 글인데 비밀번호 확인이 끝나 있으면 (다른 탭에서 먼저 저장했거나, 저장한 뒤 「뒤로」로 돌아와 다시 보낸 경우)
        // 입력한 내용을 보관해 두고 비밀번호를 다시 묻는다 → 확인하면 수정 화면에 다시 채운다
        if (item.getMemberId() == null && !verifiedItems.contains(id)) {
            FormDraft.save(request, file != null && !file.isEmpty());
            return "redirect:/items/" + id + "?reverify";
        }
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

    // 입력한 값으로 수정 화면을 다시 보여줄 때 (입력 오류, 보내지 못한 입력을 다시 채울 때)
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

    // 같은 내용인지 비교할 때의 사진 (사진만 바꿔서 다시 올리는 경우는 다른 내용으로 본다)
    static String imageIdentity(MultipartFile file) {
        return (file == null || file.isEmpty()) ? "" : file.getOriginalFilename() + ":" + file.getSize();
    }

    private static String editUrl(Long id) {
        return "/items/" + id + "/edit";
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
