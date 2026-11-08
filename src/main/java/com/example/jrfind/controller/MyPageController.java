package com.example.jrfind.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.PasswordForm;
import com.example.jrfind.domain.ProfileForm;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.LostItemService;
import com.example.jrfind.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

// 마이페이지: 내 회원정보, 내가 쓴 글·댓글, 회원정보 변경, 비밀번호 변경
// 모두 로그인이 필요하다 (SecurityConfig)
@Controller
@RequestMapping("/mypage")
public class MyPageController {
	// 탭별 목록 건수
	static final int PAGE_SIZE = 10;

	private final MemberService memberService;
	private final JrfindService foundService;
	private final LostItemService lostService;
	private final CommentService commentService;
	// 로그인 정보는 세션에 저장된다 (회원정보를 바꾸면 세션 쪽도 바꿔 줌)
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public MyPageController(MemberService memberService, JrfindService foundService, LostItemService lostService,
			CommentService commentService) {
		this.memberService = memberService;
		this.foundService = foundService;
		this.lostService = lostService;
		this.commentService = commentService;
	}

	// tab: found(見つけた) / lost(探しています) / comments(コメント)
	@GetMapping
	public String myPage(
			@RequestParam(name = "tab", defaultValue = "found") String tab,
			@RequestParam(name = "page", defaultValue = "1") int page,
			@AuthenticationPrincipal LoginMember loginMember,
			Model model) {
		Long memberId = loginMember.getId();
		model.addAttribute("member", memberService.getMember(memberId));

		int foundCount = foundService.countItemsByMember(memberId);
		int lostCount = lostService.countItemsByMember(memberId);
		int commentCount = commentService.countCommentsByMember(memberId);
		model.addAttribute("foundCount", foundCount);
		model.addAttribute("lostCount", lostCount);
		model.addAttribute("commentCount", commentCount);

		// 정해진 값 외에는 첫 번째 탭
		String currentTab = switch (tab) {
			case "lost", "comments" -> tab;
			default -> "found";
		};
		int total = switch (currentTab) {
			case "lost" -> lostCount;
			case "comments" -> commentCount;
			default -> foundCount;
		};
		int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
		int currentPage = Math.max(1, Math.min(page, totalPages));

		switch (currentTab) {
			case "lost" -> model.addAttribute("lostItems", lostService.getItemsByMember(memberId, currentPage, PAGE_SIZE));
			case "comments" -> model.addAttribute("comments", commentService.getCommentsByMember(memberId, currentPage, PAGE_SIZE));
			default -> model.addAttribute("foundItems", foundService.getItemsByMember(memberId, currentPage, PAGE_SIZE));
		}
		model.addAttribute("tab", currentTab);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("totalPages", totalPages);
		return "mypage";
	}

	// ---------- 会員情報変更 ----------

	@GetMapping("/edit")
	public String profileForm(@AuthenticationPrincipal LoginMember loginMember, Model model) {
		model.addAttribute("profileForm", ProfileForm.from(memberService.getMember(loginMember.getId())));
		return "profileEdit";
	}

	@PostMapping("/edit")
	public String updateProfile(
			@Valid @ModelAttribute("profileForm") ProfileForm form,
			BindingResult bindingResult,
			@AuthenticationPrincipal LoginMember loginMember,
			HttpServletRequest request,
			HttpServletResponse response) {
		if (memberService.displayNameOf(form.getRealName(), form.getNickname()).startsWith(MemberService.RESERVED_NAME_PREFIX)) {
			bindingResult.rejectValue("nickname", "reserved", "「" + MemberService.RESERVED_NAME_PREFIX + "」で始まる名前は使用できません。");
		}
		if (bindingResult.hasErrors()) {
			return "profileEdit"; // 입력값과 에러 메시지를 그대로 다시 보여줌
		}

		memberService.updateProfile(loginMember.getId(), form);
		refreshLoginMember(loginMember, request, response); // 헤더의 닉네임, 이후 글의 작성자명
		return "redirect:/mypage?updated";
	}

	// ---------- パスワード変更 ----------

	@GetMapping("/password")
	public String passwordForm(Model model) {
		model.addAttribute("passwordForm", new PasswordForm());
		return "passwordChange";
	}

	@PostMapping("/password")
	public String changePassword(
			@Valid @ModelAttribute("passwordForm") PasswordForm form,
			BindingResult bindingResult,
			@AuthenticationPrincipal LoginMember loginMember,
			HttpServletRequest request,
			HttpServletResponse response) {
		Long memberId = loginMember.getId();
		if (!bindingResult.hasFieldErrors("currentPassword")) {
			if (memberService.isPasswordCheckLocked(memberId)) {
				bindingResult.rejectValue("currentPassword", "locked", "入力回数の上限を超えました。しばらくしてから再度お試しください。");
			} else if (!memberService.matchesPassword(memberId, form.getCurrentPassword())) {
				bindingResult.rejectValue("currentPassword", "mismatch", "現在のパスワードが正しくありません。");
			}
		}
		if (!bindingResult.hasFieldErrors("newPassword")) {
			if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
				bindingResult.rejectValue("newPasswordConfirm", "mismatch", "パスワードが一致しません。");
			} else if (form.getNewPassword().equals(form.getCurrentPassword())) {
				bindingResult.rejectValue("newPassword", "same", "現在と同じパスワードは使用できません。");
			}
		}
		if (bindingResult.hasErrors()) {
			return "passwordChange";
		}

		memberService.changePassword(memberId, form.getNewPassword());
		refreshLoginMember(loginMember, request, response);
		return "redirect:/mypage?passwordChanged";
	}

	// 세션에 저장된 로그인 정보를 DB 의 최신 회원정보로 바꾼다 (다시 로그인하지 않아도 반영되도록)
	private void refreshLoginMember(LoginMember loginMember, HttpServletRequest request, HttpServletResponse response) {
		LoginMember updated = (LoginMember) memberService.loadUserByUsername(loginMember.getUsername());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}
