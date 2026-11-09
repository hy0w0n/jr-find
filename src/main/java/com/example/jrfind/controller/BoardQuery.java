package com.example.jrfind.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.util.StringUtils;

import com.example.jrfind.domain.Station;

import jakarta.servlet.http.HttpServletRequest;

// 게시판 목록의 조건(노선·역·카테고리·검색어·정렬)과, 그 조건을 유지한 채 이동하는 주소
// 페이지 번호·정렬 머리글·「検索をやめる」를 서버에서 만든 링크로 내보낸다 (키보드, 새 탭, JS 를 끈 브라우저에서도 동작하도록)
public record BoardQuery(String path, String line, String station, String category, String keyword, String sort) {
	private static final Set<String> SORTS = Set.of("date_desc", "date_asc", "reg_desc", "reg_asc");
	private static final String DEFAULT_SORT = "reg_desc"; // 작성일 최신순 (주소의 sort=latest 도 같다)
	private static final int PAGE_WINDOW = 2; // 지금 페이지 앞뒤로 보여 줄 페이지 수

	public BoardQuery {
		line = emptyToNull(line);
		station = emptyToNull(station);
		category = emptyToNull(category);
		sort = (sort != null && SORTS.contains(sort)) ? sort : DEFAULT_SORT;
	}

	public String pageUrl(int page) {
		return url(keyword, sort, page);
	}

	// 정렬 머리글: 지금 그 열로 내림차순이면 오름차순으로, 아니면 내림차순부터 (1페이지로)
	public String sortUrl(String column) {
		String next = (column + "_desc").equals(sort) ? column + "_asc" : column + "_desc";
		return url(keyword, next, 1);
	}

	// 검색어만 빼고 나머지 조건은 그대로
	public String withoutKeywordUrl() {
		return url(null, sort, 1);
	}

	public boolean isSortedBy(String column) {
		return sort.startsWith(column + "_");
	}

	public boolean isAscending() {
		return sort.endsWith("_asc");
	}

	// 정렬 중인 열의 aria-sort (아니면 null → 속성을 내보내지 않음)
	public String ariaSort(String column) {
		return isSortedBy(column) ? (isAscending() ? "ascending" : "descending") : null;
	}

	public boolean isFiltered() {
		return keyword != null || line != null || station != null || category != null;
	}

	// 값이 있는 조건만 붙인다. 정렬은 기본값이면, 페이지는 1이면 생략
	private String url(String keywordValue, String sortValue, int page) {
		StringJoiner query = new StringJoiner("&");
		addIfPresent(query, "line", line);
		addIfPresent(query, "station", station);
		addIfPresent(query, "category", category);
		addIfPresent(query, "q", keywordValue);
		if (!DEFAULT_SORT.equals(sortValue)) {
			addIfPresent(query, "sort", sortValue);
		}
		if (page > 1) {
			addIfPresent(query, "page", String.valueOf(page));
		}
		return (query.length() == 0) ? path : path + "?" + query;
	}

	// 검색어의 「&」「+」 등도 그대로 전달되도록 인코딩 (공백은 +)
	private static void addIfPresent(StringJoiner query, String name, String value) {
		if (value != null) {
			query.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
		}
	}

	private static String emptyToNull(String value) {
		return StringUtils.hasText(value) ? value : null;
	}

	// 페이지 번호 목록: 처음·끝과 지금 페이지 앞뒤 PAGE_WINDOW 개. 건너뛴 곳은 null (화면에서 「…」)
	// 예) 전체 20쪽, 지금 10쪽 → 1, null, 8, 9, 10, 11, 12, null, 20
	public static List<Integer> pageNumbers(int currentPage, int totalPages) {
		List<Integer> pages = new ArrayList<>();
		int from = Math.max(1, currentPage - PAGE_WINDOW);
		int to = Math.min(totalPages, currentPage + PAGE_WINDOW);
		if (from > 1) {
			pages.add(1);
			if (from > 2) {
				pages.add(null);
			}
		}
		for (int page = from; page <= to; page++) {
			pages.add(page);
		}
		if (to < totalPages) {
			if (to < totalPages - 1) {
				pages.add(null);
			}
			pages.add(totalPages);
		}
		return pages;
	}

	// 상세 화면의 「一覧」: 목록에서 왔으면 그 목록(필터·정렬·페이지 그대로), 아니면 목록 첫 화면
	public static String listUrlFrom(HttpServletRequest request, String listPath) {
		String referer = request.getHeader("Referer");
		if (referer != null) {
			try {
				URI uri = new URI(referer);
				if (request.getServerName().equalsIgnoreCase(uri.getHost()) && listPath.equals(uri.getRawPath())) {
					return listPath + ((uri.getRawQuery() != null) ? "?" + uri.getRawQuery() : "");
				}
			} catch (URISyntaxException e) {
				// 목록 첫 화면
			}
		}
		return listPath;
	}

	// 역 → 지나는 노선 (화면의 역 선택지에 data-lines 로 붙여서, 노선을 고르면 그 노선의 역만 보여 준다)
	public static Map<String, String> stationLines(List<Station> stations) {
		Map<String, List<String>> lines = new LinkedHashMap<>();
		for (Station station : stations) {
			lines.computeIfAbsent(station.getStationName(), name -> new ArrayList<>()).add(station.getLineName());
		}
		Map<String, String> joined = new LinkedHashMap<>();
		lines.forEach((name, list) -> joined.put(name, String.join(",", list)));
		return joined;
	}
}
