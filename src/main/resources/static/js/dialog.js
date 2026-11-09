// 화면 공통 확인 창 (모달)
// - 창 요소에는 role="dialog" aria-modal="true" 와 제목(aria-labelledby)을 붙여 둔다
// - 열면 창 안의 첫 버튼(또는 focus 로 지정한 요소)으로 포커스를 옮기고, Tab 은 창 안에서만 돈다
// - Esc 는 「キャンセル」과 같다 (onEscape. 지정하지 않으면 Esc 로 닫지 않는다)
// - 닫으면 열기 전에 있던 자리로 포커스를 돌려준다
const Dialog = (() => {
    const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled])';
    const states = new Map(); // 열려 있는 창 → { returnTo, onKeydown }

    function focusables(overlay) {
        return [...overlay.querySelectorAll(FOCUSABLE)].filter(el => el.offsetParent !== null);
    }

    function open(overlay, { focus = null, onEscape = null } = {}) {
        if (states.has(overlay)) close(overlay, { restoreFocus: false });
        const returnTo = document.activeElement;
        const onKeydown = (e) => {
            if (e.key === 'Escape' && onEscape) {
                e.preventDefault();
                onEscape();
                return;
            }
            if (e.key !== 'Tab') return;
            const items = focusables(overlay);
            if (items.length === 0) return;
            const first = items[0];
            const last = items[items.length - 1];
            if (e.shiftKey && (document.activeElement === first || !overlay.contains(document.activeElement))) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && (document.activeElement === last || !overlay.contains(document.activeElement))) {
                e.preventDefault();
                first.focus();
            }
        };
        states.set(overlay, { returnTo, onKeydown });
        overlay.classList.add('open');
        document.addEventListener('keydown', onKeydown, true);
        const target = (typeof focus === 'string') ? overlay.querySelector(focus) : focus;
        (target || focusables(overlay)[0] || overlay).focus();
    }

    function close(overlay, { restoreFocus = true } = {}) {
        const state = states.get(overlay);
        overlay.classList.remove('open');
        if (!state) return;
        states.delete(overlay);
        document.removeEventListener('keydown', state.onKeydown, true);
        if (restoreFocus && state.returnTo && document.contains(state.returnTo) && state.returnTo !== document.body) {
            state.returnTo.focus();
        }
    }

    return { open, close };
})();
