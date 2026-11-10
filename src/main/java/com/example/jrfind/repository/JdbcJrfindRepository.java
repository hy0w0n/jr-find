package com.example.jrfind.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException; //예외 처리용
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.jrfind.domain.Category;
import com.example.jrfind.domain.FoundItem;
import com.example.jrfind.domain.Station;
import com.example.jrfind.domain.StationLocation;

@Repository
public class JdbcJrfindRepository implements JrfindRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcJrfindRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public List<Category> findAllCategories() {
        return jdbcTemplate.query("select * from category order by id", categoryRowMapper());
    }

    @Override
    public List<String> findAllLines() {
        // distinct 만 쓰면 순서가 보장되지 않으므로 등록 순서(ID)대로 정렬
        return jdbcTemplate.queryForList("select linename from station group by linename order by min(id)", String.class);
    }

    @Override
    public List<Station> findAllStations() {
        return jdbcTemplate.query("select * from station order by id", stationRowMapper());
    }

    @Override
    public List<StationLocation> findAllStationLocations() {
        return jdbcTemplate.query("SELECT * FROM Station_Location",
            (rs, rowNum) -> new StationLocation(rs.getString("StationName"), rs.getDouble("Latitude"), rs.getDouble("Longitude")));
    }

    @Override
    public boolean existsStation(String line, String station) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM station WHERE linename = ? AND stationname = ?", Integer.class, line, station);
        return count != null && count > 0;
    }

    @Override
    public boolean existsLine(String line) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM station WHERE linename = ?", Integer.class, line);
        return count != null && count > 0;
    }

    @Override
    public boolean existsCategory(String categoryName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM category WHERE categoryname = ?", Integer.class, categoryName);
        return count != null && count > 0;
    }

    @Override
    public void save(FoundItem item) {
        // DB 컬럼 순서: Title, FoundDate, LineName, StationName, CategoryName, ItemName, Contents, ImgPath, Password, Author, RegistrationDate, MemberId
        String sql = "INSERT INTO Found_Item (Title, FoundDate, LineName, StationName, CategoryName, ItemName, Contents, ImgPath, Password, Author, RegistrationDate, MemberId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
            item.getTitle(),
            item.getFoundDate(),
            item.getLineName(),
            item.getStationName(),
            item.getCategoryName(),
            item.getItemName(),
            item.getContents(),
            item.getImgPath(), // 이미지 파일명 (없으면 null)
            item.getPassword(), // 비회원 글: BCrypt 해시 / 회원 글: null
            item.getAuthor(),
            item.getRegistrationDate(),
            item.getMemberId() // 비회원 글이면 null
        );
    }

    // 게시글 수정
    // 비밀번호, 작성자, 등록일, 삭제플래그는 수정하지 않습니다.
    @Override
    public boolean update(FoundItem item, String expectedImgPath) {
        String sql = "UPDATE Found_Item SET Title=?, FoundDate=?, LineName=?, StationName=?, CategoryName=?, ItemName=?, Contents=?, ImgPath=?, ModifiedDate=?"
                + " WHERE ID=? AND DEL_FLG = FALSE AND ImgPath IS NOT DISTINCT FROM ?";

        return jdbcTemplate.update(sql,
            item.getTitle(),
            item.getFoundDate(),
            item.getLineName(),
            item.getStationName(),
            item.getCategoryName(),
            item.getItemName(),
            item.getContents(),
            item.getImgPath(), // null이면 이미지 삭제, 값이 있으면 변경/유지
            item.getModifiedDate(),
            item.getId(),
            expectedImgPath
        ) == 1;
    }

    @Override
    public List<String> findImagePaths() {
        return jdbcTemplate.queryForList("SELECT ImgPath FROM Found_Item WHERE ImgPath IS NOT NULL", String.class);
    }

    @Override
    public List<String> findAllAuthors() {
        // '匿名の天使'로 시작하는 작성자 명 (삭제된 글의 번호는 다시 쓸 수 있다)
        String sql = "SELECT Author FROM Found_Item WHERE Author LIKE '匿名の天使%' AND DEL_FLG = FALSE";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    @Override
    public List<FoundItem> findAll(String line, String station, String category, String keyword, String sort, int page, int size) {
        // 1. 기본 SQL 및 파라미터 리스트 생성 (댓글 수 포함)
        StringBuilder sql = new StringBuilder("SELECT f.*, (SELECT count(*) FROM Post_Comment c WHERE c.FoundItemId = f.ID AND c.DEL_FLG = FALSE) AS CommentCount"
                + " FROM Found_Item f WHERE DEL_FLG = FALSE");
        List<Object> params = new ArrayList<>();

        // 2. 동적 필터링 조건 추가
        appendFilters(sql, params, line, station, category, keyword);

        // 3. 정렬 조건 추가
        // sort 파라미터 값에 따라 분기 처리 (정해진 값 외에는 SQL에 넣지 않음)
        // 날짜·일시가 같은 글이 있어도 페이지마다 순서가 바뀌지 않도록 마지막에 ID 로도 정렬한다
        if ("date_asc".equals(sort)) {
            sql.append(" ORDER BY FoundDate ASC, ID ASC");       // 발견일 오름차순 (과거 -> 최신)
        } else if ("date_desc".equals(sort)) {
            sql.append(" ORDER BY FoundDate DESC, ID DESC");     // 발견일 내림차순 (최신 -> 과거)
        } else if ("reg_asc".equals(sort)) {
            sql.append(" ORDER BY RegistrationDate ASC, ID ASC"); // 작성일 오름차순 (옛날 글 먼저)
        } else {
            // "reg_desc" 이거나 "latest" 혹은 null 일 경우 기본값: 작성일 내림차순 (최신 글 먼저)
            sql.append(" ORDER BY RegistrationDate DESC, ID DESC");
        }

        // 4. 페이징 처리 (LIMIT, OFFSET)
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add((page - 1) * size);

        return jdbcTemplate.query(sql.toString(), listRowMapper(), params.toArray());
    }

    @Override
    public List<FoundItem> findByMember(Long memberId, int page, int size) {
        String sql = "SELECT f.*, (SELECT count(*) FROM Post_Comment c WHERE c.FoundItemId = f.ID AND c.DEL_FLG = FALSE) AS CommentCount"
                + " FROM Found_Item f WHERE MemberId = ? AND DEL_FLG = FALSE ORDER BY RegistrationDate DESC, ID DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, listRowMapper(), memberId, size, (page - 1) * size);
    }

    @Override
    public Map<String, Integer> countByStation() {
        Map<String, Integer> counts = new HashMap<>();
        jdbcTemplate.query("SELECT StationName, count(*) AS Cnt FROM Found_Item WHERE DEL_FLG = FALSE GROUP BY StationName",
            rs -> {
                counts.put(rs.getString("StationName"), rs.getInt("Cnt"));
            });
        return counts;
    }

    @Override
    public int countByMember(Long memberId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM Found_Item WHERE MemberId = ? AND DEL_FLG = FALSE", Integer.class, memberId);
    }

    @Override
    public int count(String line, String station, String category, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM Found_Item WHERE DEL_FLG = FALSE");
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
        if (station != null && !station.isEmpty()) {
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
    public FoundItem findById(Long id) {
        // 삭제된(DEL_FLG = TRUE) 게시글은 URL로 직접 들어와도 보이지 않게 제외
        String sql = "SELECT * FROM Found_Item WHERE ID = ? AND DEL_FLG = FALSE";
        try {
            return jdbcTemplate.queryForObject(sql, foundItemRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public String findPasswordHash(Long id) {
        String sql = "SELECT Password FROM Found_Item WHERE ID = ? AND DEL_FLG = FALSE";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // 논리 삭제 메서드
    @Override
    public boolean delete(Long id, String expectedImgPath) {
        // 사진 파일은 서비스에서 지우므로 파일명도 비운다
        String sql = "UPDATE Found_Item SET DEL_FLG = TRUE, ImgPath = NULL WHERE ID = ? AND DEL_FLG = FALSE AND ImgPath IS NOT DISTINCT FROM ?";
        return jdbcTemplate.update(sql, id, expectedImgPath) == 1;
    }

    // [중요] DB 결과표(ResultSet)를 자바 객체(FoundItem)로 변환하는 매핑 메서드
    private RowMapper<FoundItem> foundItemRowMapper() {
        return (rs, rowNum) -> {
            FoundItem item = new FoundItem();
            item.setId(rs.getLong("ID"));
            item.setTitle(rs.getString("Title"));
            // SQL Date -> Java LocalDate 변환
            item.setFoundDate(rs.getDate("FoundDate").toLocalDate());
            item.setLineName(rs.getString("LineName"));
            item.setStationName(rs.getString("StationName"));
            item.setCategoryName(rs.getString("CategoryName"));
            item.setItemName(rs.getString("ItemName"));
            item.setContents(rs.getString("Contents"));
            item.setImgPath(rs.getString("ImgPath"));
            item.setPassword(rs.getString("Password"));
            item.setAuthor(rs.getString("Author"));
            // SQL Timestamp -> Java LocalDateTime 변환
            item.setRegistrationDate(rs.getTimestamp("RegistrationDate").toLocalDateTime());
            item.setDelFlg(rs.getBoolean("DEL_FLG"));

            java.sql.Timestamp modTs = rs.getTimestamp("ModifiedDate");
            if (modTs != null) {
                item.setModifiedDate(modTs.toLocalDateTime());
            }
            item.setMemberId(rs.getObject("MemberId", Long.class)); // NULL 이면 null
            return item;
        };
    }

    // 목록용: 게시글 + 댓글 수 (SQL 에 CommentCount 컬럼이 있어야 함)
    private RowMapper<FoundItem> listRowMapper() {
        return (rs, rowNum) -> {
            FoundItem item = foundItemRowMapper().mapRow(rs, rowNum);
            item.setCommentCount(rs.getInt("CommentCount"));
            return item;
        };
    }

    private RowMapper<Category> categoryRowMapper() {
        return (rs, rowNum) -> {
            Category category = new Category();
            category.setId(rs.getLong("id"));
            category.setCategoryName(rs.getString("categoryname"));
            return category;
        };
    }

    private RowMapper<Station> stationRowMapper() {
        return (rs, rowNum) -> {
            Station station = new Station();
            station.setId(rs.getLong("id"));
            station.setLineName(rs.getString("linename"));
            station.setStationName(rs.getString("stationname"));
            return station;
        };
    }
}
