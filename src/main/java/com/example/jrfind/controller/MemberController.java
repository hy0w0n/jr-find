package com.example.jrfind.controller;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.service.MemberService;

import jakarta.validation.Valid;

@Controller
public class MemberController {
	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	// 로그인 화면: http://localhost:8080/login
	// 로그인 처리(POST /login)와 로그아웃(POST /logout)은 Spring Security 가 처리 (SecurityConfig)
	@GetMapping("/login")
	public String loginForm(@AuthenticationPrincipal LoginMember loginMember) {
		if (loginMember != null) {
			return "redirect:/"; // 이미 로그인 상태
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
		if (memberService.displayNameOf(form).startsWith(MemberService.RESERVED_NAME_PREFIX)) {
			bindingResult.rejectValue("nickname", "reserved", "「" + MemberService.RESERVED_NAME_PREFIX + "」で始まる名前は使用できません。");
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
