// 회원 화면 공통 (로그인, 회원가입, 회원정보 변경, 비밀번호 변경, 탈퇴)
// 입력값 확인은 서버에서 하고, 여기서는 사용 편의만 더한다.
(() => {
    // 서버 검증에서 걸려 다시 열린 경우: 첫 번째로 틀린 칸으로 포커스 (오류 문구는 그 칸에 aria-describedby 로 연결돼 있다)
    const invalid = document.querySelector('[aria-invalid="true"]');
    if (invalid) invalid.focus();

    // 생년월일: 일본어 달력 (브라우저 기본 날짜 칸은 브라우저 언어로 표시된다). 1900-01-01 ~ 오늘, 직접 입력도 가능
    const birthDate = document.getElementById('birthDate');
    if (birthDate && window.flatpickr) {
        flatpickr(birthDate, {
            ...strictDateOptions(message => showFieldError(birthDate, message), '1900-01-01'),
            locale: 'ja',
            dateFormat: 'Y-m-d',
            maxDate: 'today',
            onChange: (selectedDates) => {
                if (selectedDates.length) clearFieldError(birthDate);
            }
        });
    }

    // 칸 아래의 오류 문구 (서버 검증 문구와 같은 자리·같은 모양). 스크린리더가 바로 읽도록 role="alert"
    function showFieldError(input, message) {
        const id = input.id + '-error';
        let error = document.getElementById(id);
        if (!error) {
            error = document.createElement('span');
            error.className = 'field-error';
            error.id = id;
            input.parentElement.append(error);
        }
        error.setAttribute('role', 'alert');
        error.textContent = message.replace('\n', ' ');
        input.setAttribute('aria-invalid', 'true');
        const described = (input.getAttribute('aria-describedby') || '').split(' ').filter(Boolean);
        if (!described.includes(id)) input.setAttribute('aria-describedby', [...described, id].join(' '));
        input.focus();
    }
    function clearFieldError(input) {
        const error = document.getElementById(input.id + '-error');
        if (error) error.textContent = '';
        input.removeAttribute('aria-invalid');
    }

    // 탈퇴: 되돌릴 수 없으므로 한 번 더 확인 (입력값 확인은 서버에서)
    // 비밀번호가 비었거나 확인에 체크하지 않았으면 확인창 없이 보내서 서버의 안내를 먼저 보여준다
    const withdrawForm = document.getElementById('withdrawForm');
    if (withdrawForm) {
        withdrawForm.addEventListener('submit', (e) => {
            const filled = document.getElementById('currentPassword').value !== '' && document.getElementById('agree').checked;
            if (filled && !confirm('本当に退会しますか？\nこの操作は取り消せません。')) {
                e.preventDefault();
            }
        });
    }

    // 로그인: 빈 칸이면 서버로 보내지 않고 안내
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', (e) => {
            const requiredFields = [
                { id: 'loginId', message: 'IDを入力してください。' },
                { id: 'password', message: 'パスワードを入力してください。' }
            ];
            for (const field of requiredFields) {
                const el = document.getElementById(field.id);
                if (!el.value.trim()) {
                    e.preventDefault();
                    showLoginError(field.message);
                    el.focus();
                    return;
                }
            }
        });
    }

    function showLoginError(text) {
        let box = document.getElementById('errorMessage');
        if (!box) {
            box = document.createElement('div');
            box.id = 'errorMessage';
            box.className = 'message message-error';
            box.setAttribute('role', 'alert'); // 스크린리더가 바로 읽도록
            loginForm.before(box);
        }
        box.textContent = text;
    }
})();
