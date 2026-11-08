package com.example.jrfind.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.jrfind.service.JrfindService;
import com.example.jrfind.service.LostItemService;

// 통합 검색 (헤더·홈 화면의 검색창)
// 두 게시판에서 검색어에 맞는 최신 글을 몇 건씩 보여주고, 전체 결과는 각 게시판 목록(?q=검색어)으로 연결한다.
@Controller
public class SearchController {
	// 게시판별로 미리 보여줄 건수
	static final int PREVIEW_SIZE = 5;

	private final JrfindService foundService;
	private final LostItemService lostService;

	public SearchController(JrfindService foundService, LostItemService lostService) {
		this.foundService = foundService;
		this.lostService = lostService;
	}

	@GetMapping("/search")
	public String search(@RequestParam(name = "q", required = false) String keyword, Model model) {
		String q = SearchKeyword.normalize(keyword);
		model.addAttribute("keyword", q);
		// 검색어가 없으면 입력 안내만 보여준다
		if (q == null) {
			return "search";
		}

		model.addAttribute("foundItems", foundService.searchLatest(q, PREVIEW_SIZE));
		model.addAttribute("foundCount", foundService.getTotalCount(null, null, null, q));
		model.addAttribute("lostItems", lostService.searchLatest(q, PREVIEW_SIZE));
		model.addAttribute("lostCount", lostService.getTotalCount(null, null, null, q));
		return "search";
	}
}
