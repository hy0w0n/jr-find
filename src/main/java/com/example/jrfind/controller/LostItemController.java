package com.example.jrfind.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.LostItemForm;
import com.example.jrfind.repository.KeywordCondition;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.InvalidPostException;
import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.LostItemService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

// 探しています 게시판
// 목록·상세는 누구나 볼 수 있고, 작성·수정·삭제는 로그인이 필요하다 (SecurityConfig 에서 로그인 화면으로 보냄)
@Controller
@RequestMapping("/lost-items")
public class LostItemController {
	private final LostItemService service;
	// 노선·역·카테고리 드롭다운 데이터
	private final JrfindService masterService;
	private final CommentService commentService;
	// 같은 내용을 연달아 두 번 등록해도 한 번만 저장 (세션별)
	private final SubmittedForms submittedForms;

	public LostItemController(LostItemService service, JrfindService masterService, CommentService commentService,
			SubmittedForms submittedForms) {
		this.service = service;
		this.masterService = masterService;
		this.commentService = commentService;
		this.submittedForms = submittedForms;
	}

	@GetMapping
	public String list(
			Model model,
			@RequestParam(name = "line", required = false) String line,
			@RequestParam(name = "station", required = false) String station,
			@RequestParam(name = "category", required = false) String category,
			@RequestParam(name = "q", required = false) String keyword, // 검색어 (제목·본문)
			@RequestParam(name = "sort", required = false, defaultValue = "latest") String sort,
			@RequestParam(name = "page", required = false, defaultValue = "1") int page) {
		keyword = SearchKeyword.normalize(keyword);
		int totalItems = service.getTotalCount(line, station, category, keyword);
		int totalPages = (int) Math.ceil((double) totalItems / LostItemService.PAGE_SIZE);
		int currentPage = Math.max(1, Math.min(page, totalPages));

		model.addAttribute("items", service.getItems(line, station, category, keyword, sort, currentPage));
		model.addAttribute("totalItems", totalItems);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("totalPages", totalPages);

		// 선택한 필터값 유지
		model.addAttribute("currentLine", line);
		model.addAttribute("currentStation", station);
		model.addAttribute("currentCategory", category);
		model.addAttribute("currentKeyword", keyword);
		model.addAttribute("keywordTruncated", KeywordCondition.isTruncated(keyword)); // 단어가 너무 많아 뒤는 쓰지 않음
		model.addAttribute("currentSort", sort);
		// 페이지 번호·정렬 머리글·「検索をやめる」 링크 (조건을 유지한 주소)
		model.addAttribute("query", new BoardQuery("/lost-items", line, station, category, keyword, sort));
		model.addAttribute("pageNumbers", BoardQuery.pageNumbers(currentPage, totalPages));
		addMasterData(model);
		model.addAttribute("stationLines", BoardQuery.stationLines(masterService.getStations()));
		return "lostList";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable("id") Long id, @RequestParam(name = "confirmDelete", required = false) String confirmDelete,
			@RequestParam(name = "allComments", required = false) String allComments,
			@AuthenticationPrincipal LoginMember loginMember, HttpServletRequest request, Model model) {
		LostItem item = findItemOrThrow(id);
		model.addAttribute("item", item);
		model.addAttribute("isOwner", isOwner(item, loginMember));
		// 삭제 확인 창을 연 화면 (JS 없이 「削除」를 누른 경우. JS 가 있으면 화면 안에서 연다)
		model.addAttribute("confirmDelete", confirmDelete != null && isOwner(item, loginMember));
		model.addAttribute("commentView", commentService.getComments(Board.LOST, id, allComments != null));
		CommentController.restoreCommentDraft(Board.LOST, id, loginMember, request, model);
		model.addAttribute("listUrl", BoardQuery.listUrlFrom(request, "/lost-items")); // 「一覧」: 보던 목록으로
		return "lostDetail";
	}

	@GetMapping("/new")
	public String registerForm(HttpServletRequest request, Model model) {
		// 시간이 지나 보내지 못한 입력이 있으면 다시 채운다 (ExpiredFormHandler. 로그인이 끝났으면 다시 로그인한 뒤)
		FormDraft draft = FormDraft.take(request, request.getRequestURI());
		if (draft != null) {
			model.addAttribute("form", draft.bindTo(new LostItemForm()));
			model.addAttribute("warningConfirmed", Boolean.parseBoolean(draft.get("warningConfirmed")));
			return showForm(model, null, FormErrors.withImageNotice(FormErrors.DRAFT_RESTORED, draft.imageDropped()));
		}
		model.addAttribute("form", new LostItemForm());
		return showForm(model, null, null);
	}

	@PostMapping("/new")
	public String createItem(
			@Valid @ModelAttribute("form") LostItemForm form,
			BindingResult bindingResult,
			// 작성 전 주의사항(사기·절도 경고)을 확인했는지. 화면의 확인 버튼을 누르면 true 가 들어간다.
			@RequestParam(name = "warningConfirmed", defaultValue = "false") boolean warningConfirmed,
			@RequestParam(name = "imageFile", required = false) MultipartFile file,
			@AuthenticationPrincipal LoginMember loginMember,
			Model model) {
		// 입력 오류로 화면을 다시 보여줄 때 주의사항을 또 묻지 않도록
		model.addAttribute("warningConfirmed", warningConfirmed);
		if (!warningConfirmed) {
			return showFormError(model, null, "投稿の前に注意事項を確認してください。", file, false);
		}
		if (bindingResult.hasErrors()) {
			return showFormError(model, null, FormErrors.firstMessage(bindingResult), file, false);
		}

		// 같은 내용을 방금 보냈으면 다시 저장하지 않고, 처음 보냈을 때와 같이 목록으로
		String submissionKey = SubmittedForms.key("/lost-items/new", form.getLostDate(), form.getLineName(), form.getStationName(),
			form.getCategoryName(), form.getItemName(), form.getContents(), JrfindController.imageIdentity(file));
		if (!submittedForms.begin(submissionKey)) {
			return "redirect:/lost-items";
		}
		try {
			service.saveItem(form, loginMember, file);
		} catch (InvalidPostException e) {
			submittedForms.cancel(submissionKey);
			return showFormError(model, null, e.getMessage(), file, false);
		} catch (IOException e) {
			e.printStackTrace();
			submittedForms.cancel(submissionKey);
			return showFormError(model, null, "画像の保存に失敗しました。", file, false);
		}
		return "redirect:/lost-items";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable("id") Long id, @AuthenticationPrincipal LoginMember loginMember, HttpServletRequest request, Model model) {
		LostItem item = findItemOrThrow(id);
		// 작성자 본인이 아니면 상세 화면으로 돌려보낸다
		if (!isOwner(item, loginMember)) {
			return "redirect:/lost-items/" + id;
		}
		// 시간이 지나 보내지 못한 수정 내용이 있으면 다시 채운다
		FormDraft draft = FormDraft.take(request, request.getRequestURI());
		if (draft != null) {
			model.addAttribute("form", draft.bindTo(LostItemForm.from(item)));
			return showFormError(model, item, FormErrors.withImageNotice(FormErrors.DRAFT_RESTORED, draft.imageDropped()), null,
				Boolean.parseBoolean(draft.get("isImageDeleted")));
		}
		model.addAttribute("form", LostItemForm.from(item));
		return showForm(model, item, null);
	}

	@PostMapping("/{id}/edit")
	public String updateItem(
			@PathVariable("id") Long id,
			@Valid @ModelAttribute("form") LostItemForm form,
			BindingResult bindingResult,
			@RequestParam(name = "imageFile", required = false) MultipartFile file,
			@RequestParam(name = "isImageDeleted", defaultValue = "false") boolean isImageDeleted,
			@AuthenticationPrincipal LoginMember loginMember,
			Model model) {
		LostItem item = findItemOrThrow(id);
		requireOwner(item, loginMember);

		if (bindingResult.hasErrors()) {
			return showFormError(model, item, FormErrors.firstMessage(bindingResult), file, isImageDeleted);
		}

		try {
			service.updateItem(item, form, file, isImageDeleted);
		} catch (InvalidPostException e) {
			return showFormError(model, item, e.getMessage(), file, isImageDeleted);
		} catch (IOException e) {
			e.printStackTrace();
			return showFormError(model, item, "画像の保存に失敗しました。", file, isImageDeleted);
		}
		return "redirect:/lost-items/" + id;
	}

	// 찾았음 표시 / 해제
	@PostMapping("/{id}/resolve")
	public String resolve(
			@PathVariable("id") Long id,
			@RequestParam(name = "resolved", defaultValue = "true") boolean resolved,
			@AuthenticationPrincipal LoginMember loginMember) {
		LostItem item = findItemOrThrow(id);
		requireOwner(item, loginMember);

		service.setResolved(id, resolved);
		return "redirect:/lost-items/" + id;
	}

	@PostMapping("/{id}/delete")
	public String deleteItem(@PathVariable("id") Long id, @AuthenticationPrincipal LoginMember loginMember) {
		LostItem item = findItemOrThrow(id);
		requireOwner(item, loginMember);

		service.deleteItem(id);
		return "redirect:/lost-items";
	}

	// 등록·수정 공통 화면 (item 이 null 이면 등록, 있으면 수정)
	private String showForm(Model model, LostItem item, String errorMessage) {
		model.addAttribute("item", item);
		model.addAttribute("errorMessage", errorMessage);
		addMasterData(model);
		return "lostForm";
	}

	// 입력 오류로 화면을 다시 보여줄 때 (입력한 값은 form 으로 그대로 다시 채워진다)
	// - 고른 사진은 다시 고르도록 안내
	// - 수정 화면에서 지운 사진은 지운 상태로 보여준다 (되살아난 채로 저장되지 않게)
	private String showFormError(Model model, LostItem item, String errorMessage, MultipartFile file, boolean isImageDeleted) {
		if (item != null && isImageDeleted) {
			item.setImgPath(null); // 화면 표시용 (DB 는 그대로)
			model.addAttribute("isImageDeleted", true);
		}
		return showForm(model, item, FormErrors.withImageNotice(errorMessage, file));
	}

	private void addMasterData(Model model) {
		model.addAttribute("lines", masterService.getLines());
		model.addAttribute("stations", masterService.getStations());
		model.addAttribute("categories", masterService.getCategories());
	}

	// 없는 글·삭제된 글이면 404 화면
	private LostItem findItemOrThrow(Long id) {
		LostItem item = service.getItem(id);
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return item;
	}

	private boolean isOwner(LostItem item, LoginMember loginMember) {
		return loginMember != null && item.getMemberId().equals(loginMember.getId());
	}

	// 작성자 본인이 아니면 403
	private void requireOwner(LostItem item, LoginMember loginMember) {
		if (!isOwner(item, loginMember)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
	}
}
