// 駅マップ (map.html): Leaflet + OpenStreetMap 으로 역·노선도를 그리고, 고른 역의 게시글 수를 보여준다
// 데이터는 /map/stations (JSON). 역은 지도 옆의 「駅を選ぶ」(키보드·스크린리더용)로도 고를 수 있다.
(() => {
    // 노선 표시 색 (실제 노선 색에 가깝게 고른 값). 없는 노선은 회색
    const LINE_COLORS = {
        '山手線': '#80C241',
        '京浜東北線': '#00B2E5',
        '中央・総武緩行線': '#FFD400',
        '埼京線': '#00AC9A',
        '東海道線': '#F68B1E',
        '上野東京ライン': '#B5338A'
    };
    // 순환선: 마지막 역과 첫 역을 잇는다
    const LOOP_LINES = ['山手線'];
    const colorOf = line => LINE_COLORS[line] || '#888';

    const panel = document.getElementById('stationPanel');
    const picker = document.getElementById('stationPicker');
    const fitAllButton = document.getElementById('fitAllBtn');

    // 역 이름 등은 textContent 로 넣는다 (HTML 로 해석하지 않음)
    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    // 지도 라이브러리를 불러오지 못했으면 지도 자리에 안내 (역은 「駅を選ぶ」로 고를 수 있다)
    const map = window.L ? L.map('map').setView([35.70, 139.73], 11) : null;
    if (map) {
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);
    } else {
        document.getElementById('map').replaceChildren(el('p', 'map-error', '地図を読み込めませんでした。右の「駅を選ぶ」から駅を選んでください。'));
        fitAllButton.disabled = true;
    }

    const stationsByName = new Map(); // 역 이름 → { station, marker }
    let allPoints = [];
    let selected = null;

    function markerStyle(station, isSelected) {
        const hasPosts = station.foundCount + station.lostCount > 0;
        return {
            radius: isSelected ? 10 : (hasPosts ? 8 : 6),
            color: isSelected ? '#c62828' : '#333',
            weight: isSelected ? 3 : 2,
            fillColor: hasPosts ? '#e63946' : '#fff',
            fillOpacity: 1
        };
    }

    function countLink(href, label, count) {
        const a = el('a', 'station-link');
        a.href = href;
        a.append(el('span', null, label));
        const right = el('span');
        right.append(el('b', null, String(count)), document.createTextNode('件'));
        a.append(right);
        return a;
    }

    // 역을 고르면: 지도의 표시를 바꾸고, 패널에 지나는 노선과 두 게시판의 글 수 (패널은 aria-live 라 스크린리더가 읽는다)
    function selectStation(name, { pan = false } = {}) {
        const entry = stationsByName.get(name);
        if (!entry) return;
        const { station, marker } = entry;
        if (selected?.marker) selected.marker.setStyle(markerStyle(selected.station, false));
        if (marker) {
            marker.setStyle(markerStyle(station, true));
            if (pan) map.setView([station.lat, station.lng], Math.max(map.getZoom(), 13));
        }
        selected = entry;
        if (picker.value !== name) picker.value = name;

        panel.replaceChildren();
        panel.append(el('h2', 'station-name', station.name + '駅'));

        const chips = el('div', 'line-chips');
        station.lines.forEach(line => {
            const chip = el('span', 'line-chip', line);
            chip.style.borderLeftColor = colorOf(line);
            chips.append(chip);
        });
        panel.append(chips);

        const links = el('div', 'station-links');
        const q = encodeURIComponent(station.name);
        links.append(countLink('/items?station=' + q, '見つけた', station.foundCount));
        links.append(countLink('/lost-items?station=' + q, '探しています', station.lostCount));
        panel.append(links);

        if (station.lostCenter) {
            panel.append(el('p', 'lost-center-flag', 'この駅にはJR東日本のお忘れ物承り所があります。受付時間は下の案内をご覧ください。'));
        }
    }

    function render(data) {
        const byName = new Map(data.stations.map(s => [s.name, s]));
        const legend = document.getElementById('legend');
        const markerLegend = legend.querySelector('.marker-legend');

        // 노선도: 노선 순서대로 역을 잇는다
        if (map) {
            data.lines.forEach(line => {
                const points = line.stations.map(name => byName.get(name)).filter(Boolean).map(s => [s.lat, s.lng]);
                if (LOOP_LINES.includes(line.name) && points.length > 2) points.push(points[0]);
                // interactive: false → 선이 역 위에 겹쳐도 클릭은 역으로 간다
                const layer = L.polyline(points, { color: colorOf(line.name), weight: 5, opacity: 0.85, interactive: false }).addTo(map);

                const label = el('label');
                const checkbox = el('input');
                checkbox.type = 'checkbox';
                checkbox.checked = true;
                checkbox.dataset.line = line.name;
                // 다시 켤 때는 역 아래에 그린다 (그냥 addTo 하면 맨 위에 그려져 역을 가린다)
                checkbox.addEventListener('change', () => checkbox.checked ? layer.addTo(map).bringToBack() : layer.remove());
                const swatch = el('span', 'legend-swatch');
                swatch.style.background = colorOf(line.name);
                label.append(checkbox, swatch, document.createTextNode(line.name));
                legend.insertBefore(label, markerLegend);
            });
        }

        // 역 (노선 위에 그린다)
        data.stations.forEach(station => {
            let marker = null;
            if (map) {
                const total = station.foundCount + station.lostCount;
                marker = L.circleMarker([station.lat, station.lng], markerStyle(station, false)).addTo(map);
                // 두 게시판의 건수를 나눠서 보여준다 (합계만 보이면 패널의 건수와 달라 보인다)
                marker.bindTooltip(el('span', null, total > 0 ? `${station.name} (見つけた ${station.foundCount}件・探しています ${station.lostCount}件)` : station.name),
                    { direction: 'top', offset: [0, -6] });
                marker.on('click', () => selectStation(station.name));
                // 역 47개를 Tab 으로 하나씩 지나지 않도록 Tab 순서에서는 뺀다 (키보드는 「駅を選ぶ」로 고른다)
                marker.getElement()?.setAttribute('tabindex', '-1');
                allPoints.push([station.lat, station.lng]);
            }
            stationsByName.set(station.name, { station, marker });
        });
    }

    // 「駅を選ぶ」: 고르면 바로 패널과 지도에 표시 (JS 가 없으면 아래 버튼으로 게시판 목록으로)
    picker.addEventListener('change', () => {
        if (picker.value) selectStation(picker.value, { pan: true });
    });

    fitAllButton.addEventListener('click', () => {
        if (map && allPoints.length > 0) map.fitBounds(allPoints, { padding: [20, 20] });
    });

    fetch('/map/stations')
        .then(response => {
            if (!response.ok) throw new Error(response.status);
            return response.json();
        })
        .then(render)
        .catch(error => {
            console.error(error);
            // 다시 불러오는 버튼. 역이 없으면 「全体を表示」는 쓸 수 없으므로 막는다
            const retry = el('button', 'legend-fit', '再読み込み');
            retry.type = 'button';
            retry.addEventListener('click', () => location.reload());
            panel.replaceChildren(el('p', 'map-error', '駅の情報を読み込めませんでした。'), retry);
            fitAllButton.disabled = true;
        });
})();
