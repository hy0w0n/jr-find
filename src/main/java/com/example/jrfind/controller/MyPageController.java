package com.example.jrfind.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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
import com.example.jrfind.domain.WithdrawForm;
import com.example.jrfind.service.CommentService;
import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.LostItemService;
import com.example.jrfind.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

// 마이페이지: 내 회원정보, 내가 쓴 글·댓글, 회원정보 변경, 비밀번호 변경, 탈퇴
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
	private final SessionRegistry sessionRegistry;
	// 로그인 정보는 세션에 저장된다 (회원정보를 바꾸면 세션 쪽도 바꿔 줌)
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
	private final LogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public MyPageController(MemberService memberService, JrfindService foundService, LostItemService lostService,
			CommentService commentService, SessionRegistry sessionRegistry) {
		this.memberService = memberService;
		this.foundService = foundService;
		this.lostService = lostService;
		this.commentService = commentService;
		this.sessionRegistry = sessionRegistry;
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
		if (memberService.isReservedName(memberService.displayNameOf(form.getRealName(), form.getNickname()))) {
			bindingResult.rejectValue("nickname", "reserved", MemberController.RESERVED_NAME_MESSAGE);
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
		checkCurrentPassword(memberId, form.getCurrentPassword(), bindingResult);
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

	// ---------- 退会 ----------
	// 회원정보는 지우고 글·댓글은 남긴다 (작성자명은 「退会したユーザー」). 되돌릴 수 없으므로 비밀번호를 다시 확인한다

	@GetMapping("/withdraw")
	public String withdrawForm(@AuthenticationPrincipal LoginMember loginMember, Model model) {
		addPostCounts(loginMember.getId(), model);
		model.addAttribute("withdrawForm", new WithdrawForm());
		return "withdraw";
	}

	@PostMapping("/withdraw")
	public String withdraw(
			@Valid @ModelAttribute("withdrawForm") WithdrawForm form,
			BindingResult bindingResult,
			@AuthenticationPrincipal LoginMember loginMember,
			Authentication authentication,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model) {
		Long memberId = loginMember.getId();
		checkCurrentPassword(memberId, form.getCurrentPassword(), bindingResult);
		if (bindingResult.hasErrors()) {
			addPostCounts(memberId, model);
			return "withdraw";
		}

		memberService.withdraw(memberId);
		// 다른 브라우저·기기에 로그인해 둔 세션도 끝낸다 (그쪽은 다음 요청 때 로그아웃되고 홈으로)
		sessionRegistry.getAllSessions(loginMember, false).forEach(SessionInformation::expireNow);
		// 지금 세션은 바로 로그아웃
		logoutHandler.logout(request, response, authentication);
		return "redirect:/?withdrawn";
	}

	// 탈퇴 전에 지울 글·댓글이 있는지 볼 수 있도록 건수를 보여준다
	private void addPostCounts(Long memberId, Model model) {
		model.addAttribute("foundCount", foundService.countItemsByMember(memberId));
		model.addAttribute("lostCount", lostService.countItemsByMember(memberId));
		model.addAttribute("commentCount", commentService.countCommentsByMember(memberId));
	}

	// 현재 비밀번호 확인 (비밀번호 변경, 탈퇴). 연속으로 틀리면 잠시 확인을 막는다
	private void checkCurrentPassword(Long memberId, String currentPassword, BindingResult bindingResult) {
		if (bindingResult.hasFieldErrors("currentPassword")) {
			return; // 빈 칸
		}
		if (memberService.isPasswordCheckLocked(memberId)) {
			bindingResult.rejectValue("currentPassword", "locked", "入力回数の上限を超えました。しばらくしてから再度お試しください。");
		} else if (!memberService.matchesPassword(memberId, currentPassword)) {
			bindingResult.rejectValue("currentPassword", "mismatch", "現在のパスワードが正しくありません。");
		}
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
