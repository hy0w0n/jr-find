package com.example.jrfind.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.jrfind.domain.LostItem;
import com.example.jrfind.domain.LostItemForm;

@Repository
public class JdbcLostItemRepository implements LostItemRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcLostItemRepository(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public void save(LostItem item) {
		String sql = "INSERT INTO Lost_Item (Title, LostDate, LineName, StationName, CategoryName, ItemName, Contents, ImgPath, Author, MemberId, RegistrationDate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		jdbcTemplate.update(sql,
			item.getTitle(),
			item.getLostDate(),
			item.getLineName(),
			item.getStationName(), // 역을 모르면 null
			item.getCategoryName(),
			item.getItemName(),
			item.getContents(),
			item.getImgPath(),
			item.getAuthor(),
			item.getMemberId(),
			item.getRegistrationDate()
		);
	}

	// 작성자, 등록일, 찾음 여부, 삭제플래그는 수정하지 않는다
	@Override
	public void update(LostItem item) {
		String sql = "UPDATE Lost_Item SET Title=?, LostDate=?, LineName=?, StationName=?, CategoryName=?, ItemName=?, Contents=?, ImgPath=?, ModifiedDate=? WHERE ID=?";
		jdbcTemplate.update(sql,
			item.getTitle(),
			item.getLostDate(),
			item.getLineName(),
			item.getStationName(),
			item.getCategoryName(),
			item.getItemName(),
			item.getContents(),
			item.getImgPath(),
			item.getModifiedDate(),
			item.getId()
		);
	}

	@Override
	public List<LostItem> findAll(String line, String station, String category, String keyword, String sort, int page, int size) {
		// 댓글 수 포함
		StringBuilder sql = new StringBuilder("SELECT l.*, (SELECT count(*) FROM Post_Comment c WHERE c.LostItemId = l.ID AND c.DEL_FLG = FALSE) AS CommentCount"
				+ " FROM Lost_Item l WHERE DEL_FLG = FALSE");
		List<Object> params = new ArrayList<>();
		appendFilters(sql, params, line, station, category, keyword);

		// 정해진 값 외에는 SQL 에 넣지 않는다
		// 날짜·일시가 같은 글이 있어도 페이지마다 순서가 바뀌지 않도록 마지막에 ID 로도 정렬한다
		if ("date_asc".equals(sort)) {
			sql.append(" ORDER BY LostDate ASC, ID ASC");
		} else if ("date_desc".equals(sort)) {
			sql.append(" ORDER BY LostDate DESC, ID DESC");
		} else if ("reg_asc".equals(sort)) {
			sql.append(" ORDER BY RegistrationDate ASC, ID ASC");
		} else {
			sql.append(" ORDER BY RegistrationDate DESC, ID DESC"); // 기본값: 최신 글 먼저
		}

		sql.append(" LIMIT ? OFFSET ?");
		params.add(size);
		params.add((page - 1) * size);

		return jdbcTemplate.query(sql.toString(), listRowMapper(), params.toArray());
	}

	@Override
	public List<LostItem> findByMember(Long memberId, int page, int size) {
		String sql = "SELECT l.*, (SELECT count(*) FROM Post_Comment c WHERE c.LostItemId = l.ID AND c.DEL_FLG = FALSE) AS CommentCount"
				+ " FROM Lost_Item l WHERE MemberId = ? AND DEL_FLG = FALSE ORDER BY RegistrationDate DESC, ID DESC LIMIT ? OFFSET ?";
		return jdbcTemplate.query(sql, listRowMapper(), memberId, size, (page - 1) * size);
	}

	@Override
	public Map<String, Integer> countByStation() {
		Map<String, Integer> counts = new HashMap<>();
		jdbcTemplate.query("SELECT StationName, count(*) AS Cnt FROM Lost_Item WHERE DEL_FLG = FALSE AND StationName IS NOT NULL GROUP BY StationName",
			rs -> {
				counts.put(rs.getString("StationName"), rs.getInt("Cnt"));
			});
		return counts;
	}

	@Override
	public int countByMember(Long memberId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM Lost_Item WHERE MemberId = ? AND DEL_FLG = FALSE", Integer.class, memberId);
	}

	@Override
	public int count(String line, String station, String category, String keyword) {
		StringBuilder sql = new StringBuilder("SELECT count(*) FROM Lost_Item WHERE DEL_FLG = FALSE");
		List<Object> params = new ArrayList<>();
		appendFilters(sql, params, line, station, category, keyword);

		return jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
	}

	// 목록/개수 조회 공통 필터 (값이 있는 조건만 추가)
	private void appendFilters(StringBuilder sql, List<Object> params, String line, String station, String category, String keyword) {
		if (line != null && !line.isEmpty()) {
			sql.append(" AND LineName = ?");
			params.add(line);
		}
		if (LostItemForm.UNKNOWN_STATION.equals(station)) {
			sql.append(" AND StationName IS NULL"); // 역 필터의 「駅不明」
		} else if (station != null && !station.isEmpty()) {
			sql.append(" AND StationName = ?");
			params.add(station);
		}
		if (category != null && !category.isEmpty()) {
			sql.append(" AND CategoryName = ?");
			params.add(category);
		}
		KeywordCondition.append(sql, params, keyword);
	}

	@Override
	public LostItem findById(Long id) {
		String sql = "SELECT * FROM Lost_Item WHERE ID = ? AND DEL_FLG = FALSE";
		try {
			return jdbcTemplate.queryForObject(sql, lostItemRowMapper(), id);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	@Override
	public void updateResolved(Long id, boolean resolved) {
		jdbcTemplate.update("UPDATE Lost_Item SET Resolved = ? WHERE ID = ?", resolved, id);
	}

	// 논리 삭제
	@Override
	public void delete(Long id) {
		// 사진 파일은 서비스에서 지우므로 파일명도 비운다
		jdbcTemplate.update("UPDATE Lost_Item SET DEL_FLG = TRUE, ImgPath = NULL WHERE ID = ?", id);
	}

	// 목록용: 게시글 + 댓글 수 (SQL 에 CommentCount 컬럼이 있어야 함)
	private RowMapper<LostItem> listRowMapper() {
		return (rs, rowNum) -> {
			LostItem item = lostItemRowMapper().mapRow(rs, rowNum);
			item.setCommentCount(rs.getInt("CommentCount"));
			return item;
		};
	}

	private RowMapper<LostItem> lostItemRowMapper() {
		return (rs, rowNum) -> {
			LostItem item = new LostItem();
			item.setId(rs.getLong("ID"));
			item.setTitle(rs.getString("Title"));
			item.setLostDate(rs.getDate("LostDate").toLocalDate());
			item.setLineName(rs.getString("LineName"));
			item.setStationName(rs.getString("StationName"));
			item.setCategoryName(rs.getString("CategoryName"));
			item.setItemName(rs.getString("ItemName"));
			item.setContents(rs.getString("Contents"));
			item.setImgPath(rs.getString("ImgPath"));
			item.setAuthor(rs.getString("Author"));
			item.setMemberId(rs.getLong("MemberId"));
			item.setResolved(rs.getBoolean("Resolved"));
			item.setRegistrationDate(rs.getTimestamp("RegistrationDate").toLocalDateTime());

			Timestamp modTs = rs.getTimestamp("ModifiedDate");
			if (modTs != null) {
				item.setModifiedDate(modTs.toLocalDateTime());
			}
			return item;
		};
	}
}
