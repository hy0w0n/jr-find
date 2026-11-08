package com.example.jrfind.service;

import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.Member;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.repository.MemberRepository;

// 회원가입 + 로그인 시 회원 조회 (Spring Security 가 loadUserByUsername 을 호출한다)
@Service
public class MemberService implements UserDetailsService {
	// 비회원 작성자명(匿名の天使 001 등)과 헷갈리지 않도록 회원 닉네임으로는 쓸 수 없게 한다
	public static final String RESERVED_NAME_PREFIX = "匿名の天使";

	private final MemberRepository repository;
	private final PasswordEncoder passwordEncoder;

	public MemberService(MemberRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
		Member member = repository.findByLoginId(loginId);
		if (member == null) {
			// 화면에는 "ID 또는 비밀번호가 틀림" 으로만 표시된다 (어느 쪽이 틀렸는지 알려주지 않음)
			throw new UsernameNotFoundException(loginId);
		}
		return new LoginMember(member.getId(), member.getLoginId(), member.getPassword(), member.getNickname());
	}

	public boolean isLoginIdTaken(String loginId) {
		return repository.existsByLoginId(loginId);
	}

	// 게시글에 표시될 이름: 닉네임을 비워 두면 氏名
	public String displayNameOf(SignupForm form) {
		String nickname = (form.getNickname() == null) ? "" : form.getNickname().trim();
		if (!nickname.isEmpty()) {
			return nickname;
		}
		return (form.getRealName() == null) ? "" : form.getRealName().trim();
	}

	public void register(SignupForm form) {
		Member member = new Member();
		member.setLoginId(form.getLoginId());
		member.setPassword(passwordEncoder.encode(form.getPassword()));
		member.setRealName(form.getRealName().trim());
		member.setNickname(displayNameOf(form));
		member.setBirthDate(form.getBirthDate());
		member.setPhone(form.getPhone());
		LocalDateTime now = LocalDateTime.now();
		member.setAgreedDate(now); // agree 체크는 폼 검증에서 확인 완료
		member.setRegistrationDate(now);
		repository.save(member);
	}
}
