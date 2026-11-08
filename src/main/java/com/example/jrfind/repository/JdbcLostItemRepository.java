package com.example.jrfind.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.jrfind.domain.LostItem;

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
	public List<LostItem> findAll(String line, String station, String category, String sort, int page, int size) {
		// 댓글 수 포함
		StringBuilder sql = new StringBuilder("SELECT l.*, (SELECT count(*) FROM Post_Comment c WHERE c.LostItemId = l.ID AND c.DEL_FLG = FALSE) AS CommentCount"
				+ " FROM Lost_Item l WHERE DEL_FLG = FALSE");
		List<Object> params = new ArrayList<>();
		appendFilters(sql, params, line, station, category);

		// 정해진 값 외에는 SQL 에 넣지 않는다
		if ("date_asc".equals(sort)) {
			sql.append(" ORDER BY LostDate ASC");
		} else if ("date_desc".equals(sort)) {
			sql.append(" ORDER BY LostDate DESC");
		} else if ("reg_asc".equals(sort)) {
			sql.append(" ORDER BY RegistrationDate ASC");
		} else {
			sql.append(" ORDER BY RegistrationDate DESC"); // 기본값: 최신 글 먼저
		}

		sql.append(" LIMIT ? OFFSET ?");
		params.add(size);
		params.add((page - 1) * size);

		return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
			LostItem item = lostItemRowMapper().mapRow(rs, rowNum);
			item.setCommentCount(rs.getInt("CommentCount"));
			return item;
		}, params.toArray());
	}

	@Override
	public int count(String line, String station, String category) {
		StringBuilder sql = new StringBuilder("SELECT count(*) FROM Lost_Item WHERE DEL_FLG = FALSE");
		List<Object> params = new ArrayList<>();
		appendFilters(sql, params, line, station, category);

		return jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
	}

	// 목록/개수 조회 공통 필터 (값이 있는 조건만 추가)
	private void appendFilters(StringBuilder sql, List<Object> params, String line, String station, String category) {
		if (line != null && !line.isEmpty()) {
			sql.append(" AND LineName = ?");
			params.add(line);
		}
		if (station != null && !station.isEmpty()) {
			sql.append(" AND StationName = ?");
			params.add(station);
		}
		if (category != null && !category.isEmpty()) {
			sql.append(" AND CategoryName = ?");
			params.add(category);
		}
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
		jdbcTemplate.update("UPDATE Lost_Item SET DEL_FLG = TRUE WHERE ID = ?", id);
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
