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

// 회원가입, 로그인 시 회원 조회 (Spring Security 가 loadUserByUsername 을 호출한다), 로그인 연속 실패 제한, 회원정보·비밀번호 변경
@Service
public class MemberService implements UserDetailsService {
	// 비회원 작성자명(匿名の天使 001 등)과 헷갈리지 않도록 회원 닉네임으로는 쓸 수 없게 한다
	public static final String RESERVED_NAME_PREFIX = "匿名の天使";
	private static final int LOGIN_ID_MAX_LENGTH = 20; // SignupForm 의 ID 규칙
	private static final int LOGIN_IP_MAX_FAILURES = 20;

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

	// 로그인 연속 실패 제한 (LoginAttemptGuard 에서 호출)
	// - 같은 접속 IP 에서 같은 ID 로 5번 틀리면 그 IP 에서 그 ID 의 로그인을 5분간 막는다.
	//   ID 만으로 막으면 남이 일부러 틀려서 주인의 로그인을 막을 수 있으므로 접속 IP 와 묶어서 센다.
	// - ID 를 바꿔 가며 틀려도 같은 IP 에서 LOGIN_IP_MAX_FAILURES 번이면 그 IP 의 로그인을 5분간 막는다.
	// 없는 ID 도 똑같이 세고 똑같이 잠그므로, 잠금 여부로 ID 가 있는지 알 수 없다.
	public boolean isLoginLocked(String loginId, String clientIp) {
		return attemptLimiter.isLocked(loginKey(loginId, clientIp)) || attemptLimiter.isLocked(loginIpKey(clientIp));
	}

	// 이번 실패로 잠기면 true
	public boolean recordLoginFailure(String loginId, String clientIp) {
		boolean idLocked = attemptLimiter.recordFailure(loginKey(loginId, clientIp));
		boolean ipLocked = attemptLimiter.recordFailure(loginIpKey(clientIp), LOGIN_IP_MAX_FAILURES);
		return idLocked || ipLocked;
	}

	// IP 단위 횟수는 남겨 둔다 (자기 계정에 로그인해서 횟수를 지우고 다시 시도하지 못하도록)
	public void recordLoginSuccess(String loginId, String clientIp) {
		attemptLimiter.reset(loginKey(loginId, clientIp));
	}

	private String loginKey(String loginId, String clientIp) {
		String id = (loginId == null) ? "" : loginId;
		// 가입할 수 있는 ID 는 20자까지. 그보다 긴 값은 없는 ID 이므로 잘라서 하나로 센다 (아주 긴 값으로 기록이 커지지 않게)
		if (id.length() > LOGIN_ID_MAX_LENGTH) {
			id = id.substring(0, LOGIN_ID_MAX_LENGTH + 1);
		}
		return "login:" + id + "@" + clientIp;
	}

	private String loginIpKey(String clientIp) {
		return "login-ip:" + clientIp;
	}

	public void changePassword(Long memberId, String newPassword) {
		repository.updatePassword(memberId, passwordEncoder.encode(newPassword));
	}
}
