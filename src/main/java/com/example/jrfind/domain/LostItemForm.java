package com.example.jrfind.domain;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

// 探しています 게시글 등록·수정 화면에서 받는 입력값 (서버 측 검증용)
// 제목·작성자·등록일은 사용자가 보낸 값을 쓰지 않고 서버에서 정한다.
public class LostItemForm {
	// 역 선택지 중 "駅がわからない" 의 값. 저장할 때는 역 이름 대신 NULL 로 바꾼다.
	public static final String UNKNOWN_STATION = "unknown";

	@NotNull(message = "なくした日を入力してください。")
	@PastOrPresent(message = "未来の日付は入力できません。")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate lostDate;

	@NotBlank(message = "路線を選択してください。")
	private String lineName;

	@NotBlank(message = "駅を選択してください。")
	private String stationName;

	@NotBlank(message = "カテゴリーを選択してください。")
	private String categoryName;

	@NotBlank(message = "具体的な物を入力してください。")
	@Size(max = 100, message = "具体的な物は100文字以内で入力してください。")
	private String itemName;

	@NotBlank(message = "詳細を入力してください。")
	@Size(max = 500, message = "詳細は500文字以内で入力してください。")
	private String contents;

	// 수정 화면에 기존 글 내용을 채워 넣을 때 사용
	public static LostItemForm from(LostItem item) {
		LostItemForm form = new LostItemForm();
		form.setLostDate(item.getLostDate());
		form.setLineName(item.getLineName());
		form.setStationName(item.getStationName() == null ? UNKNOWN_STATION : item.getStationName());
		form.setCategoryName(item.getCategoryName());
		form.setItemName(item.getItemName());
		form.setContents(item.getContents());
		return form;
	}

	public boolean isStationUnknown() {
		return UNKNOWN_STATION.equals(stationName);
	}

	public LocalDate getLostDate() {
		return lostDate;
	}
	public void setLostDate(LocalDate lostDate) {
		this.lostDate = lostDate;
	}
	public String getLineName() {
		return lineName;
	}
	public void setLineName(String lineName) {
		this.lineName = lineName;
	}
	public String getStationName() {
		return stationName;
	}
	public void setStationName(String stationName) {
		this.stationName = stationName;
	}
	public String getCategoryName() {
		return categoryName;
	}
	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}
	public String getItemName() {
		return itemName;
	}
	public void setItemName(String itemName) {
		this.itemName = itemName;
	}
	public String getContents() {
		return contents;
	}
	// 줄바꿈은 LF 로 맞춘다 (브라우저는 CRLF 로 보내서, 화면에서는 500자 이내여도 서버에서 넘칠 수 있다)
	public void setContents(String contents) {
		this.contents = Texts.normalizeNewlines(contents);
	}
}
