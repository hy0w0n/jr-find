// 게시글 상세 화면 공통 (見つけた itemDetail, 探しています lostDetail)
// 수정·삭제 버튼은 서버가 만든 링크라 JS 가 없어도 동작한다 (서버가 비밀번호 확인 창·삭제 확인 창을 연 화면을 돌려준다).
// JS 가 있으면 화면을 이동하지 않고 그 창을 열고, 비밀번호는 화면을 바꾸지 않고 확인한다.
(() => {
    const $ = (id) => document.getElementById(id);

    function openDialog(dialog) {
        Dialog.open(dialog, { focus: dialog.querySelector('[data-autofocus]'), onEscape: () => closeDialog(dialog) });
    }
    function closeDialog(dialog) {
        Dialog.close(dialog);
        // 서버가 창을 연 주소(?verify=, ?confirmDelete 등)였으면 주소에서 지운다 (새로고침해도 다시 열리지 않게)
        if (location.search) history.replaceState(null, '', location.pathname + location.hash);
    }

    // 창을 여는 링크(data-dialog): 이동하지 않고 창을 연다
    document.querySelectorAll('a[data-dialog]').forEach(link => link.addEventListener('click', (e) => {
        e.preventDefault();
        if (link.dataset.action) preparePassword(link.dataset.action);
        openDialog($(link.dataset.dialog));
    }));
    // 창 안의 「キャンセル」(링크): 닫기만
    document.querySelectorAll('.modal-overlay a.modal-btn-cancel').forEach(link => link.addEventListener('click', (e) => {
        e.preventDefault();
        closeDialog(link.closest('.modal-overlay'));
    }));

    // ---------- 見つけた 비회원 글: 4자리 비밀번호 확인 ----------
    const pwModal = $('passwordModal');
    const pwForm = $('pwForm');
    const pwInput = $('modalPasswordInput');
    const pwError = $('modalErrorMsg');
    const pwDesc = $('pwModalDesc');
    const pwDescDefault = pwDesc ? pwDesc.textContent : '';
    let afterMessage = null; // 확인할 수 없는 상태(화면이 오래됨·글이 삭제됨)를 알린 뒤 OK 를 누르면 할 일

    function preparePassword(action) {
        $('pwAction').value = action;
        pwDesc.textContent = pwDescDefault;
        pwError.textContent = '';
        pwInput.value = '';
        pwInput.removeAttribute('aria-invalid');
        afterMessage = null;
    }
    function showPasswordError(message) {
        pwError.textContent = message;
        pwInput.setAttribute('aria-invalid', 'true');
        pwInput.value = '';
        pwInput.focus();
    }

    if (pwForm) {
        pwForm.addEventListener('submit', (e) => {
            e.preventDefault();
            if (afterMessage) {
                afterMessage();
                return;
            }
            const password = pwInput.value;
            if (!/^\d{4}$/.test(password)) {
                // 틀린 횟수로 세지 않도록 서버에 보내기 전에 막는다
                showPasswordError(password ? 'パスワードは半角数字4桁で入力してください。' : 'パスワードを入力してください。');
                return;
            }
            // 비밀번호는 URL 이 아니라 요청 본문으로, CSRF 토큰은 <meta> 에서 읽어서 헤더로 보낸다
            const csrfToken = document.querySelector('meta[name="_csrf"]').content;
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
            fetch(pwForm.dataset.checkUrl, {
                method: 'POST',
                headers: { [csrfHeader]: csrfToken },
                body: new URLSearchParams({ password })
            })
            .then(response => {
                // 연속으로 틀려서 잠긴 경우 ({ ok: false, remaining: 0, lockMinutes })
                if (response.status === 429) return response.json().then(body => ({ ...body, locked: true }));
                // 화면을 오래 열어 둬서 세션이 끝난 경우 (CSRF 토큰이 맞지 않음)
                if (response.status === 403) return 'expired';
                // 다른 탭 등에서 이미 삭제된 글
                if (response.status === 404) return 'deleted';
                if (!response.ok) throw new Error(response.status);
                return response.json();
            })
            .then(result => {
                if (result === 'expired' || result === 'deleted') {
                    pwError.textContent = (result === 'expired')
                        ? '画面を開いてから時間が経ったため、確認できませんでした。\n「OK」を押すと、画面を読み込み直します。'
                        : 'この投稿は削除されています。\n「OK」を押すと、一覧に戻ります。';
                    afterMessage = (result === 'expired') ? () => location.reload() : () => { location.href = pwForm.dataset.listUrl; };
                    pwForm.querySelector('.modal-btn-ok').focus();
                    return;
                }
                if (result.locked) {
                    showPasswordError(`入力回数の上限を超えました。\n${result.lockMinutes}分ほど経ってから再度お試しください。`);
                    return;
                }
                // { ok, remaining: 몇 번 더 틀리면 잠기는지, lockMinutes }
                if (result.ok) {
                    if ($('pwAction').value === 'delete') {
                        Dialog.close(pwModal, { restoreFocus: false });
                        openDialog($('deleteModal'));
                    } else {
                        location.href = pwForm.dataset.editUrl;
                    }
                    return;
                }
                // 잠기기까지 얼마 남지 않았으면 미리 알린다
                showPasswordError('パスワードが間違っています。\nもう一度入力してください。'
                    + ((result.remaining <= 3) ? `\n(あと${result.remaining}回間違えると、${result.lockMinutes}分間入力できなくなります。)` : ''));
            })
            .catch(error => {
                console.error('Error:', error);
                showPasswordError('サーバーと通信できませんでした。\nしばらくしてから再度お試しください。');
            });
        });
    }

    // 서버가 처음부터 연 창 (JS 없이 ?verify= 로 왔거나, 수정을 보냈는데 비밀번호 확인이 풀려 있던 경우 등):
    // 포커스·Tab·Esc 를 맞춘다
    document.querySelectorAll('.modal-overlay.open').forEach(openDialog);

    // 「シェア」 (js/share-link.js)
    $('shareButton')?.addEventListener('click', shareLink);
})();
