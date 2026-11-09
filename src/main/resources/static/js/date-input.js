// 달력 입력 칸(flatpickr, 직접 입력 허용) 공통 설정: 見つけた 등록·수정, 探しています 등록·수정
// flatpickr 는 직접 입력한 값이 형식에 맞지 않아도 날짜로 바꿔 버린다 (20261101 → 2026-10-01, 2026-02-30 → 2026-03-02).
// YYYY-MM-DD 형식의 실제로 있는 오늘 이전 날짜만 받고, 아니면 칸을 비운 뒤 onInvalid(안내 문구)를 부른다.
//   사용법: flatpickr('#id', { ...strictDateOptions(message => showModal(...)), locale: 'ja', ... })
function strictDateOptions(onInvalid) {
    return {
        allowInput: true,
        minDate: '2000-01-01', // 서버의 하한과 같다 (LostItemService.MIN_POST_DATE)
        parseDate: (text) => {
            const message = checkTypedDate(text);
            if (message) {
                setTimeout(() => onInvalid(message)); // flatpickr 가 칸을 비운 뒤에 안내
                return undefined;
            }
            const [y, m, d] = text.trim().split('-').map(Number);
            return new Date(y, m - 1, d);
        },
        errorHandler: () => {} // 위에서 화면으로 안내하므로 콘솔 경고는 생략
    };
}

// 문제가 없으면 null, 있으면 안내 문구 (등록 화면의 제출 전 검사와 같은 문구)
function checkTypedDate(text) {
    const value = text.trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return '日付の形式が正しくありません。\n(例: 2026-01-01)';
    }
    const [y, m, d] = value.split('-').map(Number);
    const date = new Date(y, m - 1, d);
    if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) {
        return '存在しない日付です。';
    }
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (date > today) {
        return '未来の日付は入力できません。';
    }
    if (date < new Date(2000, 0, 1)) {
        return '2000年1月1日以降の日付を入力してください。';
    }
    return null;
}
