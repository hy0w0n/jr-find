package com.example.jrfind.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.jrfind.domain.Member;

@Repository
public class JdbcMemberRepository implements MemberRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcMemberRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void save(Member member) {
        String sql = "INSERT INTO Member (LoginId, Password, RealName, Nickname, BirthDate, Phone, AgreedDate, RegistrationDate) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
            member.getLoginId(),
            member.getPassword(), // BCrypt 해시
            member.getRealName(),
            member.getNickname(),
            member.getBirthDate(),
            member.getPhone(),
            member.getAgreedDate(),
            member.getRegistrationDate()
        );
    }

    @Override
    public Member findByLoginId(String loginId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM Member WHERE LoginId = ?", memberRowMapper(), loginId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    // 대소문자만 다른 ID 도 이미 있는 것으로 본다 (demo_user 와 DEMO_USER 처럼 헷갈리는 ID 를 만들지 못하게)
    public boolean existsByLoginId(String loginId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM Member WHERE LOWER(LoginId) = LOWER(?)", Integer.class, loginId);
        return count != null && count > 0;
    }

    @Override
    public Member findById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM Member WHERE ID = ?", memberRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void updateProfile(Member member) {
        String sql = "UPDATE Member SET RealName = ?, Nickname = ?, BirthDate = ?, Phone = ? WHERE ID = ?";
        jdbcTemplate.update(sql,
            member.getRealName(),
            member.getNickname(),
            member.getBirthDate(),
            member.getPhone(),
            member.getId()
        );
    }

    @Override
    public void updatePassword(Long id, String passwordHash) {
        jdbcTemplate.update("UPDATE Member SET Password = ? WHERE ID = ?", passwordHash, id);
    }

    @Override
    public void updateAuthorName(Long memberId, String nickname) {
        jdbcTemplate.update("UPDATE Found_Item SET Author = ? WHERE MemberId = ?", nickname, memberId);
        jdbcTemplate.update("UPDATE Lost_Item SET Author = ? WHERE MemberId = ?", nickname, memberId);
        jdbcTemplate.update("UPDATE Post_Comment SET Author = ? WHERE MemberId = ?", nickname, memberId);
    }

    @Override
    public void withdraw(Long id, String nickname, LocalDateTime withdrawnDate) {
        String sql = "UPDATE Member SET LoginId = NULL, Password = NULL, RealName = NULL, Nickname = ?, BirthDate = NULL, Phone = NULL, WithdrawnDate = ?"
            + " WHERE ID = ? AND WithdrawnDate IS NULL";
        jdbcTemplate.update(sql, nickname, withdrawnDate, id);
    }

    private RowMapper<Member> memberRowMapper() {
        return (rs, rowNum) -> {
            Member member = new Member();
            member.setId(rs.getLong("ID"));
            member.setLoginId(rs.getString("LoginId"));
            member.setPassword(rs.getString("Password"));
            member.setRealName(rs.getString("RealName"));
            member.setNickname(rs.getString("Nickname"));
            member.setBirthDate(rs.getObject("BirthDate", LocalDate.class)); // 탈퇴한 회원은 NULL
            member.setPhone(rs.getString("Phone"));
            member.setAgreedDate(rs.getTimestamp("AgreedDate").toLocalDateTime());
            member.setRegistrationDate(rs.getTimestamp("RegistrationDate").toLocalDateTime());
            member.setWithdrawnDate(rs.getObject("WithdrawnDate", LocalDateTime.class));
            return member;
        };
    }
}
