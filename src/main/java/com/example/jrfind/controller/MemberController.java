package com.example.jrfind.controller;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
public class MemberController {
	// 회원가입, 회원정보 변경(MyPageController) 공통
	static final String RESERVED_NAME_MESSAGE = "「" + MemberService.RESERVED_NAME_PREFIX + "」で始まる名前や「"
			+ MemberService.WITHDRAWN_NAME + "」は使用できません。";

	private final MemberService memberService;
	// 로그인이 필요한 화면에서 로그인 화면으로 올 때 Spring Security 가 원래 화면을 저장해 두는 곳 (기본 설정과 같음)
	private final RequestCache requestCache = new HttpSessionRequestCache();

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	// 로그인 화면: http://localhost:8080/login
	// 로그인 처리(POST /login)와 로그아웃(POST /logout)은 Spring Security 가 처리 (SecurityConfig)
	@GetMapping("/login")
	public String loginForm(@AuthenticationPrincipal LoginMember loginMember, HttpServletRequest request, HttpServletResponse response) {
		if (loginMember != null) {
			return "redirect:/"; // 이미 로그인 상태
		}
		// 헤더의 「ログイン」 등으로 직접 연 경우: 예전에 로그인이 필요해서 막혔던 화면은 잊는다
		// (남겨 두면 한참 뒤에 로그인했을 때 그 화면, 예를 들어 탈퇴 화면으로 이동해 버린다)
		// 로그인이 필요한 화면에서 넘어온 경우(?required)와 그 뒤의 실패·가입 완료(?error 등)는 원래 화면으로 돌아가도록 남긴다
		if (request.getParameterMap().isEmpty()) {
			requestCache.removeRequest(request, response);
		}
		return "login";
	}

	// 회원가입 화면: http://localhost:8080/signup
	@GetMapping("/signup")
	public String signupForm(@AuthenticationPrincipal LoginMember loginMember, Model model) {
		if (loginMember != null) {
			return "redirect:/";
		}
		model.addAttribute("signupForm", new SignupForm());
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("signupForm") SignupForm form, BindingResult bindingResult) {
		// 폼 어노테이션만으로는 확인할 수 없는 항목
		if (!bindingResult.hasFieldErrors("password") && !form.getPassword().equals(form.getPasswordConfirm())) {
			bindingResult.rejectValue("passwordConfirm", "mismatch", "パスワードが一致しません。");
		}
		if (!bindingResult.hasFieldErrors("loginId") && memberService.isLoginIdTaken(form.getLoginId())) {
			bindingResult.rejectValue("loginId", "duplicate", "このIDは既に使用されています。");
		}
		if (memberService.isReservedName(memberService.displayNameOf(form))) {
			bindingResult.rejectValue("nickname", "reserved", RESERVED_NAME_MESSAGE);
		}
		if (bindingResult.hasErrors()) {
			return "signup"; // 입력값과 에러 메시지를 그대로 다시 보여줌
		}

		try {
			memberService.register(form);
		} catch (DuplicateKeyException e) {
			// 위에서 중복을 확인한 직후 다른 사람이 같은 ID로 가입한 경우
			bindingResult.rejectValue("loginId", "duplicate", "このIDは既に使用されています。");
			return "signup";
		}
		return "redirect:/login?registered";
	}
}
