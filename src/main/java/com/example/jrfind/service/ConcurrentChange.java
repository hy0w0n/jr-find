package com.example.jrfind.service;

// 같은 게시글을 동시에 수정·삭제했을 때 (JrfindService, LostItemService)
// 수정·삭제는 "읽었을 때와 그대로인 경우만" 반영하므로(Repository 의 update·delete), 늦게 온 쪽은 반영되지 않는다.
final class ConcurrentChange {
	// 삭제하는 중에 사진이 바뀌면 다시 읽어서 지운다 (보통은 한두 번이면 끝난다)
	static final int MAX_ATTEMPTS = 5;

	private ConcurrentChange() {
	}

	// 수정을 반영하지 못했을 때의 안내 (deleted: 그사이 글이 삭제됨)
	static String message(boolean deleted) {
		return deleted
			? "この投稿は削除されています。"
			: "ほかの画面で同時に変更されたため、保存できませんでした。もう一度お試しください。";
	}
}
