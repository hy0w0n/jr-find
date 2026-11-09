// <head> 에서 불러온다: JS 가 켜져 있으면 <html class="js">
// JS 가 꺼져 있을 때를 위한 모양(필터의 「絞り込む」 버튼, 探しています 주의사항을 폼 위에 표시)과
// 켜져 있을 때의 모양을 CSS 에서 나눈다. 화면을 그리기 전에 붙여야 바뀌는 모습이 보이지 않는다.
document.documentElement.classList.add('js');
