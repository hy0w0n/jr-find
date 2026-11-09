// 게시판 목록 공통 (見つけた ItemList, 探しています lostList)
// 필터·정렬·페이지는 모두 서버가 만든 링크와 폼이라 JS 없이도 동작한다. 여기서는 사용 편의만 더한다.
document.documentElement.classList.add('js'); // 「絞り込む」 버튼을 키보드로 왔을 때만 보이게 (board-list.css)

(() => {
    const form = document.getElementById('filterForm');
    const lineSelect = document.getElementById('lineFilter');
    const stationSelect = document.getElementById('stationFilter');
    const selects = [lineSelect, stationSelect, document.getElementById('categoryFilter')];

    // 노선을 고르면 그 노선의 역만, 역을 고르면 그 역을 지나는 노선만 보여 준다 (data-lines: 역을 지나는 노선)
    // 「駅不明」처럼 data-lines 가 없는 선택지는 항상 보인다
    function narrow() {
        const line = lineSelect.value;
        for (const option of stationSelect.options) {
            if (option.dataset.lines) {
                option.hidden = line !== '' && !option.dataset.lines.split(',').includes(line);
            }
        }
        if (stationSelect.selectedOptions[0]?.hidden) {
            stationSelect.value = '';
        }
        const stationLines = stationSelect.selectedOptions[0]?.dataset.lines?.split(',');
        for (const option of lineSelect.options) {
            if (option.value !== '') {
                option.hidden = stationLines !== undefined && !stationLines.includes(option.value);
            }
        }
    }

    // 마우스·터치로 고르면 바로 적용한다.
    // 키보드 ↑↓ 로 값을 바꾸는 중에는 화면을 바꾸지 않는다 (고른 뒤 「絞り込む」 버튼으로 적용)
    let pointerUsed = false;
    for (const select of selects) {
        select.addEventListener('pointerdown', () => { pointerUsed = true; });
        select.addEventListener('keydown', () => { pointerUsed = false; });
        select.addEventListener('change', () => {
            narrow();
            if (pointerUsed) {
                form.requestSubmit();
            }
        });
    }

    // 고르지 않은 조건(빈 값)은 주소에 붙이지 않는다 (?line=&station= 처럼 되지 않도록)
    form.addEventListener('formdata', (e) => {
        for (const [name, value] of [...e.formData.entries()]) {
            if (value === '') e.formData.delete(name);
        }
    });

    // 게시판 안 검색: 빈 칸이면 보내지 않는다 (검색 중이었다면 검색어만 지운다)
    const search = document.getElementById('boardSearch');
    search.addEventListener('submit', (e) => {
        const input = search.querySelector('input[name="q"]');
        if (input.value.trim() !== '') return;
        e.preventDefault();
        if (search.dataset.clearUrl) {
            location.href = search.dataset.clearUrl;
        } else {
            input.focus();
        }
    });
})();
