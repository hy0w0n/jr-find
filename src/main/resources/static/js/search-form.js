// 헤더·홈의 통합 검색 (form[data-site-search]): 빈 칸(공백만)이면 보내지 않는다
// 홈 화면에서는 이 스크립트가 검색 폼보다 먼저 불러와지므로 문서 전체에서 받는다
document.addEventListener('submit', (e) => {
    const form = e.target;
    if (!form.matches('form[data-site-search]')) return;
    if (form.q.value.trim() === '') {
        e.preventDefault();
        form.q.focus();
    }
});
