// 상세 화면의 「シェア」: 이 글의 주소를 복사한다 (見つけた・探しています 상세)
// 클립보드를 쓸 수 없는 환경(https 가 아닌 주소 등)에서는 주소를 직접 복사할 수 있도록 보여준다
function shareLink() {
    const url = location.origin + location.pathname; // ?expired 같은 안내용 값은 빼고 글 주소만
    const showUrl = () => window.prompt('このページのリンクです。コピーしてお使いください。', url);
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(url).then(() => showShareToast('リンクをコピーしました。'), showUrl);
    } else {
        showUrl();
    }
}

// 잠깐 보였다가 사라지는 안내
function showShareToast(message) {
    let toast = document.getElementById('shareToast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'shareToast';
        toast.setAttribute('role', 'status');
        toast.style.cssText = 'position: fixed; left: 50%; bottom: 40px; transform: translateX(-50%); padding: 10px 22px;'
            + ' border-radius: 20px; background: rgba(0, 0, 0, 0.75); color: #fff; font-size: 14px; z-index: 2000;';
        document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.style.display = 'block';
    clearTimeout(toast.hideTimer);
    toast.hideTimer = setTimeout(() => toast.style.display = 'none', 2000);
}
