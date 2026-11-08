package com.example.jrfind.repository;

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
    public boolean existsByLoginId(String loginId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM Member WHERE LoginId = ?", Integer.class, loginId);
        return count != null && count > 0;
    }

    private RowMapper<Member> memberRowMapper() {
        return (rs, rowNum) -> {
            Member member = new Member();
            member.setId(rs.getLong("ID"));
            member.setLoginId(rs.getString("LoginId"));
            member.setPassword(rs.getString("Password"));
            member.setRealName(rs.getString("RealName"));
            member.setNickname(rs.getString("Nickname"));
            member.setBirthDate(rs.getDate("BirthDate").toLocalDate());
            member.setPhone(rs.getString("Phone"));
            member.setAgreedDate(rs.getTimestamp("AgreedDate").toLocalDateTime());
            member.setRegistrationDate(rs.getTimestamp("RegistrationDate").toLocalDateTime());
            return member;
        };
    }
}
