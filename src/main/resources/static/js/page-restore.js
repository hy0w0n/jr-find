// 모든 화면 공통 (헤더에서 불러옴): 뒤로·앞으로 가기로 브라우저가 저장해 둔 화면을 그대로 다시 보여줄 때(bfcache)
// 로그인 상태로 그린 화면이면 서버에서 새로 불러온다 (로그아웃한 뒤 「뒤로」로 마이페이지 등 개인정보가 보이지 않도록)
// 서버도 로그아웃할 때 세션 쿠키와 저장된 화면을 지우게 하지만(SecurityConfig), 그 지시를 따르지 않는 브라우저 대비
// 주소는 그대로 GET 으로 다시 연다 (POST 결과 화면이었어도 다시 보내지 않도록 reload 는 쓰지 않음)
window.addEventListener('pageshow', (e) => {
    if (e.persisted && document.querySelector('form[action="/logout"]')) {
        location.replace(location.pathname + location.search);
    }
});
