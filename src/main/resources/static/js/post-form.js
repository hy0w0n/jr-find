// 게시글 등록·수정 화면 공통 (見つけた registerForm·editForm, 探しています lostForm)
// 필요한 값은 화면의 마크업에서 읽는다 (form.post-form, data-required 의 안내 문구, 각 입력 칸의 id)
// JS 가 꺼져 있어도 폼은 보낼 수 있고 서버가 다시 검사한다. 여기서는 미리 확인과 사용 편의를 더한다.
(() => {
    const form = document.querySelector('form.post-form');
    const $ = (id) => document.getElementById(id);
    const dateInput = form.querySelector('.date-input');
    const lineSelect = $('lineSelect');
    const stationSelect = $('stationSelect');

    // ---------- 안내·확인 창 ----------
    const modal = $('customModal');
    let onConfirm = null;
    let onCancel = null;

    // message 를 창으로 보여 준다. withCancel 이면 「キャンセル」도 (Esc 도 キャンセル)
    function showMessage(message, okCallback = null, withCancel = false, cancelCallback = null) {
        $('modalMessage').innerText = message;
        $('modalCancelBtn').hidden = !withCancel;
        onConfirm = okCallback;
        onCancel = cancelCallback;
        Dialog.open(modal, { focus: '#modalOkBtn', onEscape: withCancel ? cancelModal : confirmModal });
    }
    function confirmModal() {
        const action = onConfirm;
        onConfirm = onCancel = null;
        Dialog.close(modal, { restoreFocus: !action });
        if (action) action();
    }
    function cancelModal() {
        const action = onCancel;
        onConfirm = onCancel = null;
        Dialog.close(modal);
        if (action) action();
    }
    $('modalOkBtn').addEventListener('click', confirmModal);
    $('modalCancelBtn').addEventListener('click', cancelModal);

    // ---------- 자동 제목 (서버에서도 같은 형식으로 다시 만든다) ----------
    function updateTitle() {
        const station = (stationSelect.value === 'unknown') ? '駅不明' : stationSelect.value;
        const parts = [dateInput.value.trim(), lineSelect.value, station, $('categorySelect').value];
        const item = $('itemName').value.trim();
        $('autoTitle').value = (parts.every(Boolean) && item) ? `[${parts.join('][')}] ${item}` : '';
    }

    // ---------- 달력 (라이브러리를 불러오지 못해도 직접 입력은 된다) ----------
    if (window.flatpickr) {
        flatpickr(dateInput, {
            // 직접 입력한 날짜가 틀리면 칸을 비우고 안내 (date-input.js)
            ...strictDateOptions(message => showMessage(message, () => dateInput.focus())),
            locale: 'ja',
            dateFormat: 'Y-m-d',
            maxDate: 'today',
            onChange: updateTitle,
            onReady: (selectedDates, dateStr, instance) => {
                // 달력 아래의 「本日」「クリア」
                const footer = document.createElement('div');
                footer.className = 'calendar-footer';
                const today = document.createElement('button');
                today.type = 'button';
                today.textContent = '本日';
                today.addEventListener('click', () => instance.setDate(new Date(), true));
                const clear = document.createElement('button');
                clear.type = 'button';
                clear.textContent = 'クリア';
                clear.addEventListener('click', () => instance.clear());
                footer.append(today, clear);
                instance.calendarContainer.appendChild(footer);
            }
        });
    }

    // ---------- 노선 → 그 노선의 역만 (data-line 이 없는 「駅を選択」「駅がわからない」는 항상) ----------
    function narrowStations() {
        const line = lineSelect.value;
        for (const option of stationSelect.options) {
            if (option.dataset.line) {
                option.hidden = option.disabled = (option.dataset.line !== line);
            }
        }
        if (stationSelect.selectedOptions[0]?.disabled) {
            stationSelect.value = '';
        }
    }
    lineSelect.addEventListener('change', () => { narrowStations(); updateTitle(); });
    [stationSelect, $('categorySelect')].forEach(el => el.addEventListener('change', updateTitle));
    [dateInput, $('itemName')].forEach(el => el.addEventListener('input', updateTitle));

    // ---------- 사진 (서버와 같은 기준: JPG・PNG・GIF・WEBP, 10MB 이하) ----------
    const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    const MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    const fileInput = $('fileInput');
    const preview = $('imagePreview');
    const deleteButton = $('deleteImgBtn');
    const isImageDeleted = $('isImageDeleted'); // 수정 화면만: 기존 사진을 지웠는지

    function showPreview(src) {
        preview.src = src;
        preview.hidden = false;
        deleteButton.hidden = false;
        $('uploadPlaceholder').hidden = true;
    }
    function clearPreview() {
        preview.removeAttribute('src');
        preview.hidden = true;
        deleteButton.hidden = true;
        $('uploadPlaceholder').hidden = false;
    }

    fileInput.addEventListener('change', () => {
        const file = fileInput.files && fileInput.files[0];
        if (!file) return;
        if (!ALLOWED_IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_SIZE) {
            fileInput.value = '';
            showMessage('10MB以下の画像ファイル(JPG・PNG・GIF・WEBP)を選択してください。', () => fileInput.focus());
            return;
        }
        const reader = new FileReader();
        reader.onload = (e) => {
            // 확장자만 바꾼 파일(.txt → .jpg 등)은 그림으로 열 수 없다 → 깨진 미리 보기 대신 안내 (서버에서도 다시 확인)
            const probe = new Image();
            probe.onload = () => {
                showPreview(e.target.result);
                if (isImageDeleted) isImageDeleted.value = 'false';
            };
            probe.onerror = () => {
                fileInput.value = '';
                showMessage('画像を読み込めませんでした。\n画像ファイル(JPG・PNG・GIF・WEBP)を選択してください。', () => fileInput.focus());
            };
            probe.src = e.target.result;
        };
        reader.readAsDataURL(file);
    });

    deleteButton.addEventListener('click', () => {
        fileInput.value = '';
        clearPreview();
        if (isImageDeleted) isImageDeleted.value = 'true'; // 수정 화면: 기존 사진 삭제
        fileInput.focus();
    });

    // ---------- 비밀번호 확인 (비회원 등록만) ----------
    const password = $('password');
    const confirmPassword = $('confirmPassword');
    if (password && confirmPassword) {
        const check = () => {
            const mismatch = confirmPassword.value !== '' && password.value !== confirmPassword.value;
            $('pwErrorMsg').hidden = !mismatch;
            confirmPassword.setAttribute('aria-invalid', mismatch);
        };
        password.addEventListener('input', check);
        confirmPassword.addEventListener('input', check);
    }

    // ---------- 보내기 전 확인 (위 칸부터 하나씩. 서버에서도 다시 확인한다) ----------
    let submitting = false;
    function firstProblem() {
        const date = dateInput.value.trim();
        if (!date) return [dateInput.dataset.required, dateInput];
        const dateMessage = checkTypedDate(date);
        if (dateMessage) return [dateMessage, dateInput];
        for (const el of form.querySelectorAll('[data-required]')) {
            if (el !== dateInput && !el.value.trim()) return [el.dataset.required, el];
        }
        if (password && !/^\d{4}$/.test(password.value)) return ['パスワードは半角数字4桁で入力してください。', password];
        if (password && password.value !== confirmPassword.value) return ['パスワードが一致しません。', confirmPassword];
        return null;
    }
    form.addEventListener('submit', (e) => {
        const problem = firstProblem();
        if (problem) {
            e.preventDefault();
            const [message, field] = problem;
            showMessage(message, () => field.focus());
            return;
        }
        submitting = true;
    });

    // 입력 칸에서 Enter 를 눌러도 바로 보내지 않는다 (날짜 칸은 달력이 입력값을 확인한다)
    form.querySelectorAll('input[type="text"], input[type="password"]').forEach(input => {
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') e.preventDefault();
        });
    });

    // ---------- 다른 화면으로 나가기 전 확인 (입력한 내용이 있을 때만) ----------
    // 화면 안의 링크(キャンセル·로고·헤더)는 이 화면의 창으로, 뒤로 가기·새로고침·탭 닫기는 브라우저의 확인 창으로
    let dirty = form.dataset.dirty === 'true'; // 서버 검증에서 걸렸거나, 보내지 못한 입력을 다시 채운 경우
    form.addEventListener('input', () => { dirty = true; });
    form.addEventListener('change', () => { dirty = true; });
    window.addEventListener('beforeunload', (e) => {
        if (dirty && !submitting) {
            e.preventDefault();
            e.returnValue = '';
        }
    });
    document.addEventListener('click', (e) => {
        const link = e.target.closest('a[href]');
        if (!link || !dirty || submitting || link.target || e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0
                || link.getAttribute('href').startsWith('#') || modal.contains(link)) {
            return;
        }
        e.preventDefault();
        showMessage('ページから移動しますか？\n変更内容は保存されません。', () => {
            submitting = true; // 확인했으므로 브라우저 확인 창은 띄우지 않는다
            location.href = link.href;
        }, true);
    });

    // ---------- 探しています: 작성 전 주의사항 (확인해야 작성할 수 있다) ----------
    const warning = $('warningModal');
    const warningCheck = $('warningCheck');
    if (warning) {
        warning.setAttribute('role', 'dialog');
        warning.setAttribute('aria-modal', 'true');
        warning.setAttribute('aria-labelledby', 'warningTitle');
        const okButton = $('warningOkBtn');
        okButton.disabled = !warningCheck.checked;
        warningCheck.addEventListener('change', () => { okButton.disabled = !warningCheck.checked; });
        okButton.addEventListener('click', () => {
            Dialog.close(warning, { restoreFocus: false });
            dateInput.focus();
        });
    }

    // ---------- 처음 표시 ----------
    narrowStations();
    updateTitle();
    const serverError = $('serverError'); // 서버 검증에서 걸린 경우 (JS 가 없으면 화면에 그대로 보인다)
    if (warning && !warningCheck.checked) {
        Dialog.open(warning, { focus: warningCheck });
    } else if (serverError) {
        serverError.hidden = true;
        showMessage(serverError.textContent.trim());
    }
})();
