package com.example.jrfind.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.jrfind.domain.MapData;
import com.example.jrfind.domain.Station;
import com.example.jrfind.domain.StationLocation;
import com.example.jrfind.repository.JrfindRepository;
import com.example.jrfind.repository.LostItemRepository;

// 駅マップ: 역 위치, 노선(노선도), 역별 게시글 수
@Service
public class MapService {
	// JR東日本 お忘れ物承り所가 있는 역 (JR東日本 공식 사이트 안내 기준. 시간은 fragments/lostCenter.html)
	static final Set<String> LOST_CENTER_STATIONS = Set.of("東京", "大宮");

	private final JrfindRepository repository;
	private final LostItemRepository lostRepository;

	public MapService(JrfindRepository repository, LostItemRepository lostRepository) {
		this.repository = repository;
		this.lostRepository = lostRepository;
	}

	public MapData getMapData() {
		// STATION 은 노선별로 역 순서대로 등록돼 있다 (ID 순)
		Map<String, List<String>> lineStations = new LinkedHashMap<>(); // 노선 → 역 (노선 순서)
		Map<String, List<String>> stationLines = new LinkedHashMap<>(); // 역 → 지나는 노선
		for (Station station : repository.findAllStations()) {
			lineStations.computeIfAbsent(station.getLineName(), k -> new ArrayList<>()).add(station.getStationName());
			stationLines.computeIfAbsent(station.getStationName(), k -> new ArrayList<>()).add(station.getLineName());
		}

		Map<String, Integer> foundCounts = repository.countByStation();
		Map<String, Integer> lostCounts = lostRepository.countByStation();

		// 위치가 등록된 역만 지도에 표시
		List<MapData.StationMarker> markers = new ArrayList<>();
		for (StationLocation location : repository.findAllStationLocations()) {
			String name = location.stationName();
			if (!stationLines.containsKey(name)) {
				continue;
			}
			markers.add(new MapData.StationMarker(name, location.latitude(), location.longitude(), stationLines.get(name),
				foundCounts.getOrDefault(name, 0), lostCounts.getOrDefault(name, 0), LOST_CENTER_STATIONS.contains(name)));
		}

		List<MapData.Line> lines = lineStations.entrySet().stream()
			.map(entry -> new MapData.Line(entry.getKey(), entry.getValue()))
			.toList();
		return new MapData(lines, markers);
	}
}
