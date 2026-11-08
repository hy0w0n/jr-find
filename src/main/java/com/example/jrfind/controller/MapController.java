package com.example.jrfind.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.jrfind.domain.MapData;
import com.example.jrfind.service.MapService;

// 駅マップ (헤더의 지도 아이콘): 역 위치와 노선도, 역별 게시글 수, お忘れ物 문의처
// 지도는 화면에서 Leaflet + OpenStreetMap 으로 그리고, 데이터는 /map/stations 에서 받는다
@Controller
public class MapController {
	private final MapService mapService;

	public MapController(MapService mapService) {
		this.mapService = mapService;
	}

	@GetMapping("/map")
	public String map() {
		return "map";
	}

	@GetMapping("/map/stations")
	@ResponseBody
	public MapData stations() {
		return mapService.getMapData();
	}
}
