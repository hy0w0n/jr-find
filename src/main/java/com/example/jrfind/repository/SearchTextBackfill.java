package com.example.jrfind.repository;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

// 검색용 컬럼(SEARCHTEXT)이 비어 있는 글을 채운다
// - 이 컬럼을 추가하기 전에 만들어진 DB 의 글 (서버를 켤 때 한 번. 화면을 열기 전에 끝난다)
// - 저장소를 거치지 않고 SQL 로 직접 넣은 글 (테스트 데이터 등)
// 글을 저장·수정할 때는 저장소가 함께 채우므로, 보통은 비어 있는 글이 없다.
@Component
public class SearchTextBackfill {
	private static final Logger log = LoggerFactory.getLogger(SearchTextBackfill.class);
	private static final int BATCH_SIZE = 500;

	private final JdbcTemplate jdbcTemplate;

	public SearchTextBackfill(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostConstruct
	public void fillMissing() {
		int filled = fill("Found_Item") + fill("Lost_Item");
		if (filled > 0) {
			log.info("검색용 컬럼을 채움: {}건", filled);
		}
	}

	private int fill(String table) {
		// 테이블 이름은 위의 두 개만 쓴다 (사용자 입력이 아님)
		List<Object[]> rows = jdbcTemplate.query("SELECT ID, Title, Contents FROM " + table + " WHERE SearchText IS NULL",
			(rs, rowNum) -> new Object[] { KeywordCondition.searchText(rs.getString("Title"), rs.getString("Contents")), rs.getLong("ID") });
		for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
			jdbcTemplate.batchUpdate("UPDATE " + table + " SET SearchText = ? WHERE ID = ?",
				rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE)));
		}
		return rows.size();
	}
}
