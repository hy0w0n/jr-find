// 모든 화면 공통 (헤더에서 불러옴): 같은 폼을 두 번 보내지 않게 한다 (등록 버튼 더블클릭 등)
// 화면의 입력 검사에서 막힌 제출(preventDefault)은 건드리지 않고, 실제로 보내는 POST 만 한 번으로 막는다.
document.addEventListener('submit', (e) => {
    const form = e.target;
    if (e.defaultPrevented || form.method.toLowerCase() !== 'post') return;
    if (form.dataset.submitting) {
        e.preventDefault();
        return;
    }
    form.dataset.submitting = 'true';
    // 버튼을 바로 막으면 이번 전송에서 버튼 값이 빠질 수 있으므로, 전송이 시작된 뒤에 막는다
    setTimeout(() => submitButtons(form).forEach(button => button.disabled = true));
});

// 뒤로 가기로 돌아온 화면(브라우저가 저장해 둔 화면)은 새로 불러온다 (page-restore.js)

// 폼 안의 제출 버튼 + 폼 밖에서 form 속성으로 연결된 제출 버튼
function submitButtons(form) {
    const inside = [...form.querySelectorAll('button:not([type="button"]), input[type="submit"]')];
    const outside = form.id ? [...document.querySelectorAll(`button[form="${form.id}"]:not([type="button"])`)] : [];
    return inside.concat(outside);
}
