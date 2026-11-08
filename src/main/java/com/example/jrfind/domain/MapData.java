package com.example.jrfind.domain;

import java.util.List;

// 駅マップ 화면에 JSON 으로 보내는 데이터
// lines    : 노선별 역 이름 (노선을 따라가는 순서. 노선도 선을 그릴 때 사용)
// stations : 역 위치와 역별 게시글 수
public record MapData(List<Line> lines, List<StationMarker> stations) {

	public record Line(String name, List<String> stations) {
	}

	// lostCenter: JR東日本 お忘れ物承り所가 있는 역
	public record StationMarker(String name, double lat, double lng, List<String> lines,
			int foundCount, int lostCount, boolean lostCenter) {
	}
}
