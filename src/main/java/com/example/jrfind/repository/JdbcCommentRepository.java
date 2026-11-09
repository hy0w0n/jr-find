package com.example.jrfind.repository;

import java.sql.PreparedStatement;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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

	// 새 댓글 번호를 돌려준다 (등록 후 그 댓글 위치로 이동하기 위해)
	@Override
	public Long save(Board board, Long postId, Comment comment) {
		String sql = "INSERT INTO Post_Comment (" + board.getCommentColumn() + ", Contents, Author, MemberId, RegistrationDate) VALUES (?, ?, ?, ?, ?)";
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
			ps.setLong(1, postId);
			ps.setString(2, comment.getContents());
			ps.setString(3, comment.getAuthor());
			ps.setLong(4, comment.getMemberId());
			ps.setObject(5, comment.getRegistrationDate());
			return ps;
		}, keyHolder);
		return keyHolder.getKey().longValue();
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

	// 댓글이 달린 게시글(삭제되지 않은 것)과 함께 조회. 어느 게시판 글인지는 어느 쪽이 연결됐는지로 판단
	private static final String MEMBER_COMMENTS_FROM = " FROM Post_Comment c"
			+ " LEFT JOIN Found_Item f ON c.FoundItemId = f.ID AND f.DEL_FLG = FALSE"
			+ " LEFT JOIN Lost_Item l ON c.LostItemId = l.ID AND l.DEL_FLG = FALSE"
			+ " WHERE c.MemberId = ? AND c.DEL_FLG = FALSE AND (f.ID IS NOT NULL OR l.ID IS NOT NULL)";

	@Override
	public List<Comment> findByMember(Long memberId, int page, int size) {
		String sql = "SELECT c.*, f.Title AS FoundTitle, l.Title AS LostTitle" + MEMBER_COMMENTS_FROM
				+ " ORDER BY c.RegistrationDate DESC, c.ID DESC LIMIT ? OFFSET ?";
		return jdbcTemplate.query(sql, (rs, rowNum) -> {
			Comment comment = commentRowMapper().mapRow(rs, rowNum);
			if (rs.getString("FoundTitle") != null) {
				comment.setBoard(Board.FOUND);
				comment.setPostId(rs.getLong("FoundItemId"));
				comment.setPostTitle(rs.getString("FoundTitle"));
			} else {
				comment.setBoard(Board.LOST);
				comment.setPostId(rs.getLong("LostItemId"));
				comment.setPostTitle(rs.getString("LostTitle"));
			}
			return comment;
		}, memberId, size, (page - 1) * size);
	}

	@Override
	public int countByMember(Long memberId) {
		return jdbcTemplate.queryForObject("SELECT count(*)" + MEMBER_COMMENTS_FROM, Integer.class, memberId);
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
