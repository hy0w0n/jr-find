package com.example.jrfind.repository;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.jrfind.domain.Board;
import com.example.jrfind.domain.Comment;

// 게시판별 컬럼명(FoundItemId / LostItemId)은 Board 에 정해진 값만 SQL 에 들어간다
@Repository
public class JdbcCommentRepository implements CommentRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcCommentRepository(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public void save(Board board, Long postId, Comment comment) {
		String sql = "INSERT INTO Post_Comment (" + board.getCommentColumn() + ", Contents, Author, MemberId, RegistrationDate) VALUES (?, ?, ?, ?, ?)";
		jdbcTemplate.update(sql,
			postId,
			comment.getContents(),
			comment.getAuthor(),
			comment.getMemberId(),
			comment.getRegistrationDate()
		);
	}

	@Override
	public List<Comment> findByPost(Board board, Long postId) {
		String sql = "SELECT * FROM Post_Comment WHERE " + board.getCommentColumn() + " = ? AND DEL_FLG = FALSE ORDER BY RegistrationDate ASC, ID ASC";
		return jdbcTemplate.query(sql, commentRowMapper(), postId);
	}

	@Override
	public Comment findById(Board board, Long postId, Long id) {
		String sql = "SELECT * FROM Post_Comment WHERE ID = ? AND " + board.getCommentColumn() + " = ? AND DEL_FLG = FALSE";
		try {
			return jdbcTemplate.queryForObject(sql, commentRowMapper(), id, postId);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	// 논리 삭제
	@Override
	public void delete(Long id) {
		jdbcTemplate.update("UPDATE Post_Comment SET DEL_FLG = TRUE WHERE ID = ?", id);
	}

	private RowMapper<Comment> commentRowMapper() {
		return (rs, rowNum) -> {
			Comment comment = new Comment();
			comment.setId(rs.getLong("ID"));
			comment.setContents(rs.getString("Contents"));
			comment.setAuthor(rs.getString("Author"));
			comment.setMemberId(rs.getLong("MemberId"));
			comment.setRegistrationDate(rs.getTimestamp("RegistrationDate").toLocalDateTime());
			return comment;
		};
	}
}
