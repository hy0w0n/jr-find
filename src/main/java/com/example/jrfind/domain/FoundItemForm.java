package com.example.jrfind.domain;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

// 게시글 등록·수정 화면에서 받는 입력값 (서버 측 검증용)
// 제목·작성자·등록일 등은 사용자가 보낸 값을 쓰지 않고 서버에서 정한다.
public class FoundItemForm {
	@NotNull(message = "発見した日を入力してください。")
	@PastOrPresent(message = "未来の日付は入力できません。")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate foundDate;

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

	public LocalDate getFoundDate() {
		return foundDate;
	}
	public void setFoundDate(LocalDate foundDate) {
		this.foundDate = foundDate;
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
	// 앞뒤 공백·보이지 않는 글자는 지운다 (보이지 않는 글자만 있으면 빈 값 → 「具体的な物を入力してください」)
	public void setItemName(String itemName) {
		this.itemName = Texts.strip(itemName);
	}
	public String getContents() {
		return contents;
	}
	// 줄바꿈은 LF 로 맞춘다 (브라우저는 CRLF 로 보내서, 화면에서는 500자 이내여도 서버에서 넘칠 수 있다)
	// 앞뒤 공백·보이지 않는 글자도 지운다
	public void setContents(String contents) {
		this.contents = Texts.strip(Texts.normalizeNewlines(contents));
	}
}
