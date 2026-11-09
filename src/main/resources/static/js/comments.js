// 댓글 영역 (見つけた / 探しています 상세 화면 공통, fragments/comments.html). 로그인한 회원에게만 불러온다
(() => {
    const form = document.getElementById('commentForm');
    const textarea = document.getElementById('commentContents');
    const length = document.getElementById('commentLength');
    const error = document.getElementById('commentError');

    const updateLength = () => { length.innerText = textarea.value.length; };
    textarea.addEventListener('input', updateLength);
    updateLength();

    // 빈 댓글은 보내지 않는다 (서버에서도 다시 확인)
    form.addEventListener('submit', (e) => {
        if (textarea.value.trim() === '') {
            e.preventDefault();
            error.innerText = 'コメントを入力してください。';
            textarea.setAttribute('aria-invalid', 'true');
            textarea.focus();
        }
    });
    textarea.addEventListener('input', () => textarea.removeAttribute('aria-invalid'));

    // 댓글 삭제: 한 번 더 확인
    document.querySelectorAll('form.comment-delete-form').forEach(deleteForm => {
        deleteForm.addEventListener('submit', (e) => {
            if (!confirm('コメントを削除しますか？')) e.preventDefault();
        });
    });
})();
