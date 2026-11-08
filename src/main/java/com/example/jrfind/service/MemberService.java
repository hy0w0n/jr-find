package com.example.jrfind.service;

import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jrfind.domain.LoginMember;
import com.example.jrfind.domain.Member;
import com.example.jrfind.domain.ProfileForm;
import com.example.jrfind.domain.SignupForm;
import com.example.jrfind.repository.MemberRepository;

// 회원가입, 로그인 시 회원 조회 (Spring Security 가 loadUserByUsername 을 호출한다), 회원정보·비밀번호 변경
@Service
public class MemberService implements UserDetailsService {
	// 비회원 작성자명(匿名の天使 001 등)과 헷갈리지 않도록 회원 닉네임으로는 쓸 수 없게 한다
	public static final String RESERVED_NAME_PREFIX = "匿名の天使";

	private final MemberRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordAttemptLimiter attemptLimiter;

	public MemberService(MemberRepository repository, PasswordEncoder passwordEncoder, PasswordAttemptLimiter attemptLimiter) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.attemptLimiter = attemptLimiter;
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
	public String displayNameOf(String realName, String nickname) {
		String trimmed = (nickname == null) ? "" : nickname.trim();
		if (!trimmed.isEmpty()) {
			return trimmed;
		}
		return (realName == null) ? "" : realName.trim();
	}

	public String displayNameOf(SignupForm form) {
		return displayNameOf(form.getRealName(), form.getNickname());
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

	// 없으면 null
	public Member getMember(Long id) {
		return repository.findById(id);
	}

	// 닉네임이 바뀌면 지금까지 쓴 게시글·댓글의 작성자명도 함께 바꾼다
	@Transactional
	public void updateProfile(Long memberId, ProfileForm form) {
		Member member = new Member();
		member.setId(memberId);
		member.setRealName(form.getRealName().trim());
		member.setNickname(displayNameOf(form.getRealName(), form.getNickname()));
		member.setBirthDate(form.getBirthDate());
		member.setPhone(form.getPhone());
		repository.updateProfile(member);
		repository.updateAuthorName(memberId, member.getNickname());
	}

	// 현재 비밀번호 확인 (비밀번호 변경 화면). 연속으로 틀리면 잠시 확인을 막는다
	public boolean isPasswordCheckLocked(Long memberId) {
		return attemptLimiter.isLocked(attemptKey(memberId));
	}

	public boolean matchesPassword(Long memberId, String rawPassword) {
		Member member = repository.findById(memberId);
		boolean matches = member != null && rawPassword != null && passwordEncoder.matches(rawPassword, member.getPassword());
		if (matches) {
			attemptLimiter.reset(attemptKey(memberId));
		} else {
			attemptLimiter.recordFailure(attemptKey(memberId));
		}
		return matches;
	}

	private String attemptKey(Long memberId) {
		return "member:" + memberId;
	}

	public void changePassword(Long memberId, String newPassword) {
		repository.updatePassword(memberId, passwordEncoder.encode(newPassword));
	}
}
