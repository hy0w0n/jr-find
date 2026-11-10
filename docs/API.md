# API 명세 (HTTP 엔드포인트)

**한국어** | [日本語](API.ja.md) · [README](../README.md) · [구조·DB 설계](ARCHITECTURE.md)

JR Find 는 서버에서 화면(Thymeleaf)을 만들어 돌려주는 웹 애플리케이션입니다. 대부분의 엔드포인트는 HTML 화면이나 리다이렉트(등록·수정 후 PRG)를 돌려주고,
화면의 JS 가 쓰는 JSON 엔드포인트는 3개입니다 (`/items/{id}/check-password`, `/map/stations`, `/csrf`).
화면 문구와 오류 메시지는 일본어입니다. 아래 경로는 `http://localhost:8080` 기준입니다.

## 목차

- [요약](#요약)
- [공통 규칙](#공통-규칙)
- [1. 홈·통합 검색](#1-홈통합-검색)
- [2. 見つけた 게시판](#2-見つけた-게시판)
- [3. 探しています 게시판](#3-探しています-게시판)
- [4. 댓글](#4-댓글)
- [5. 회원가입·로그인](#5-회원가입로그인)
- [6. 마이페이지](#6-마이페이지)
- [7. 駅マップ](#7-駅マップ)
- [8. 유틸리티·정적 파일](#8-유틸리티정적-파일)
- [JSON 엔드포인트 예시](#json-엔드포인트-예시)
- [보안 헤더](#보안-헤더)

## 요약

엔드포인트 **40개** (컨트롤러 38 + Spring Security 가 처리하는 `POST /login`, `POST /logout`). 정적 파일 경로는 따로 정리했습니다.

권한 표기: **누구나** / **회원** (로그인 필요) / **작성자** (그 글·댓글을 쓴 회원) / **확인된 세션** (비회원 글의 4자리 비밀번호 확인을 통과한 세션)

| # | 메서드 | 경로 | 권한 | 설명 | 응답 |
|---:|---|---|---|---|---|
| 1 | GET | `/` | 누구나 | 홈 (통합 검색창, 두 게시판 입구) | 화면 |
| 2 | GET | `/search` | 누구나 | 통합 검색 (두 게시판 최신 5건씩 + 건수) | 화면 |
| 3 | GET | `/items` | 누구나 | 見つけた 목록 (필터·검색·정렬·페이지) | 화면 |
| 4 | GET | `/items/new` | 누구나 | 見つけた 등록 화면 | 화면 |
| 5 | POST | `/items/new` | 누구나 | 見つけた 등록 (비회원은 4자리 비밀번호) | 302 |
| 6 | GET | `/items/{id}` | 누구나 | 見つけた 상세 | 화면 |
| 7 | POST | `/items/{id}/check-password` | 누구나 | 비회원 글 비밀번호 확인 (JS) | JSON |
| 8 | POST | `/items/{id}/verify` | 누구나 | 비회원 글 비밀번호 확인 (JS 없이) | 302 |
| 9 | GET | `/items/{id}/edit` | 작성자 / 확인된 세션 | 見つけた 수정 화면 | 화면 / 302 |
| 10 | POST | `/items/{id}/edit` | 작성자 / 확인된 세션 | 見つけた 수정 | 302 |
| 11 | POST | `/items/{id}/delete` | 작성자 / 확인된 세션 | 見つけた 삭제 (논리 삭제) | 302 |
| 12 | GET | `/lost-items` | 누구나 | 探しています 목록 | 화면 |
| 13 | GET | `/lost-items/{id}` | 누구나 | 探しています 상세 | 화면 |
| 14 | GET | `/lost-items/new` | 회원 | 探しています 등록 화면 (작성 전 주의사항) | 화면 |
| 15 | POST | `/lost-items/new` | 회원 | 探しています 등록 | 302 |
| 16 | GET | `/lost-items/{id}/edit` | 작성자 | 探しています 수정 화면 | 화면 / 302 |
| 17 | POST | `/lost-items/{id}/edit` | 작성자 | 探しています 수정 | 302 |
| 18 | POST | `/lost-items/{id}/resolve` | 작성자 | 「見つかりました」 표시·해제 | 302 |
| 19 | POST | `/lost-items/{id}/delete` | 작성자 | 探しています 삭제 (논리 삭제) | 302 |
| 20 | GET | `/items/{id}/comments` | 회원 | 로그인 후 見つけた 상세의 댓글 위치로 | 302 |
| 21 | POST | `/items/{id}/comments` | 회원 | 見つけた 댓글 등록 | 302 |
| 22 | POST | `/items/{id}/comments/{commentId}/delete` | 작성자 | 見つけた 댓글 삭제 | 302 |
| 23 | GET | `/lost-items/{id}/comments` | 회원 | 로그인 후 探しています 상세의 댓글 위치로 | 302 |
| 24 | POST | `/lost-items/{id}/comments` | 회원 | 探しています 댓글 등록 | 302 |
| 25 | POST | `/lost-items/{id}/comments/{commentId}/delete` | 작성자 | 探しています 댓글 삭제 | 302 |
| 26 | GET | `/login` | 누구나 | 로그인 화면 | 화면 |
| 27 | POST | `/login` | 누구나 | 로그인 (Spring Security, 연속 실패 제한) | 302 |
| 28 | POST | `/logout` | 누구나 | 로그아웃 (Spring Security) | 302 |
| 29 | GET | `/signup` | 누구나 | 회원가입 화면 | 화면 |
| 30 | POST | `/signup` | 누구나 | 회원가입 | 302 / 화면 |
| 31 | GET | `/mypage` | 회원 | 마이페이지 (내 글·댓글 탭) | 화면 |
| 32 | GET | `/mypage/edit` | 회원 | 회원정보 변경 화면 | 화면 |
| 33 | POST | `/mypage/edit` | 회원 | 회원정보 변경 | 302 / 화면 |
| 34 | GET | `/mypage/password` | 회원 | 비밀번호 변경 화면 | 화면 |
| 35 | POST | `/mypage/password` | 회원 | 비밀번호 변경 | 302 / 화면 |
| 36 | GET | `/mypage/withdraw` | 회원 | 탈퇴 화면 | 화면 |
| 37 | POST | `/mypage/withdraw` | 회원 | 탈퇴 | 302 / 화면 |
| 38 | GET | `/map` | 누구나 | 駅マップ 화면 | 화면 |
| 39 | GET | `/map/stations` | 누구나 | 역 위치·노선·역별 글 수 | JSON |
| 40 | GET | `/csrf` | 누구나 | CSRF 토큰 (JS 가 보내기 직전에 받음) | JSON |

## 공통 규칙

### 권한

- 로그인이 필요한 경로 (`SecurityConfig`): `/lost-items/new`, `/lost-items/*/edit`, `/lost-items/*/delete`, `/lost-items/*/resolve`,
  `/items/*/comments/**`, `/lost-items/*/comments/**`, `/mypage/**`.
  로그인하지 않고 오면 `302 /login?required` 로 보내고, 로그인한 뒤 원래 화면으로 돌아옵니다.
- 그 밖의 경로는 누구나 열 수 있고, 게시글·댓글의 수정·삭제 권한은 각 컨트롤러가 확인합니다.
  - 회원 글: 작성자 본인만. 비회원 글(見つけた): 4자리 비밀번호 확인을 통과한 세션만 (`VerifiedItems`, 세션 범위)
  - 권한 없이 수정·삭제를 보내면 `403`. 없는 글·삭제된 글은 `404` (오류 화면)

### CSRF

- 모든 POST 에 CSRF 토큰이 필요합니다. 폼은 hidden 값 `_csrf`(`th:action` 이 자동으로 넣음), JS 의 `fetch` 는 헤더 `X-CSRF-TOKEN`.
- 토큰을 만들면 세션이 생기므로, 로그인하지 않은 방문자가 글을 보기만 하는 화면(見つけた 상세 등)에는 토큰을 넣지 않습니다.
  JS 는 보내기 직전에 `GET /csrf` 로 토큰을 받고, 받아 둔 토큰이 세션 만료로 무효(`403`)면 새로 받아 한 번만 다시 보냅니다.
  JS 없이 서버가 비밀번호 창·삭제 확인 창을 연 화면(`?verify=`, `?confirmDelete`)과 작성자 본인의 화면에만 폼에 토큰을 넣습니다.

### 오래 열어 둔 폼 (`ExpiredFormHandler`)

- 화면의 폼(`_csrf` 파라미터가 있는 요청)을 보냈는데 토큰이 맞지 않으면 `403` 대신 보던 화면(같은 사이트의 Referer)에 `?expired` 를 붙여 돌려보내고 안내합니다.
- 게시글 등록·수정(`/items/new`, `/items/*/edit`, `/lost-items/new`, `/lost-items/*/edit`)과 댓글 등록은 입력한 내용을 보관해 두고(`FormDraft`: 세션에 1건, 2,000자까지)
  같은 폼을 다시 열 때 채웁니다. 로그인이 끝났으면 다시 로그인한 뒤, 비회원 글은 비밀번호를 다시 확인한 뒤 채웁니다. 사진은 보관하지 않으므로 다시 고르도록 안내합니다.
- `POST /logout` 이면 홈으로. 화면의 폼이 아닌 요청(fetch)은 그대로 `403`.

### 같은 내용을 다시 보냄 (`SubmittedForms`)

- 같은 세션이 같은 곳에 같은 내용(사진은 파일명·크기)을 **10분** 안에 다시 보내면 한 번만 저장하고, 처음 보냈을 때와 같은 화면으로 이동합니다.
  대상: `POST /items/new`, `POST /lost-items/new`, 댓글 등록. (등록 후 「뒤로」로 돌아와 다시 보내거나 버튼을 연달아 누른 경우)

### 시도 횟수 제한 (`PasswordAttemptLimiter`)

| 대상 | 세는 기준 | 한도 | 잠금 | 잠겼을 때 |
|---|---|---|---|---|
| 비회원 글 4자리 비밀번호 | 게시글 + 접속 IP | 5회 | 5분 | `check-password` 는 `429`, `verify` 는 안내 문구 |
| 로그인 | ID + 접속 IP / 접속 IP | 5회 / 20회 | 5분 | `302 /login?locked` (비밀번호를 확인하지 않음) |
| 마이페이지 현재 비밀번호 (비밀번호 변경·탈퇴) | 회원 | 5회 | 5분 | 입력 칸의 오류 문구 |

- 마지막으로 틀린 뒤 5분이 지나면 처음부터 셉니다. 남은 횟수가 3번 이하면 화면에서 안내합니다.
- 비회원 글 비밀번호는 NFKC 로 맞춘 뒤(전각 숫자 → 반각) 4자리 숫자가 아니면 틀린 횟수로 세지 않습니다. 없는 글·회원 글은 `404` 이고 세지 않습니다.
- 접속 IP 는 `request.getRemoteAddr()` 만 씁니다 (`X-Forwarded-For` 는 요청하는 쪽이 바꿀 수 있으므로).

### 입력값

- 서버에서 다시 검증합니다 (Bean Validation + 노선·역·카테고리가 마스터 데이터에 있는지). 형식 오류는 같은 화면을 다시 보여주며 입력값은 유지합니다.
- 검증 전에 정리: 줄바꿈은 LF, 앞뒤 공백(전각·NBSP)과 폭 없는 글자는 삭제, 글자 방향을 바꾸는 제어 문자(U+202A–202E, U+2066–2069)는 어디에 있든 삭제.
- 사진: 파일 앞부분(매직 넘버)으로 JPG·PNG·GIF·WEBP 만 허용, 파일·요청 크기 10MB 까지, 5,000만 화소를 넘으면 거부.
  긴 변이 1,280px 를 넘는 JPG·PNG 는 줄이고, JPG 는 다시 저장해 촬영 정보(EXIF·GPS)를 지웁니다. 저장 파일명은 서버에서 만듭니다 (`yyyyMMddHHmmss_8자리.확장자`).

## 1. 홈·통합 검색

### `GET /`
- 응답: 화면 `home`

### `GET /search`
| 파라미터 | 설명 |
|---|---|
| `q` | 검색어. 앞뒤 공백 제거, 비어 있으면 입력 안내만 표시. 공백(전각 포함)으로 나눈 단어 **모두** 포함하는 글 (최대 5단어, 넘으면 `keywordTruncated` 안내) |

- 응답: 화면 `search` — 두 게시판에서 최신 5건(`foundItems`, `lostItems`)과 전체 건수(`foundCount`, `lostCount`). 전체 결과는 각 게시판 목록 `?q=` 로 연결.
- 비교 방식: 제목+본문을 NFKC·소문자로 맞춘 `SEARCHTEXT` 컬럼에 대해 `LIKE`. 대소문자·전각·반각 구분 없음 (ｽﾏﾎ = スマホ, Ｓｕｉｃａ = suica). `%` `_` 는 글자 그대로 검색.

## 2. 見つけた 게시판

### `GET /items`
| 파라미터 | 설명 |
|---|---|
| `line`, `station`, `category` | 노선·역·카테고리 필터 (값이 있는 것만) |
| `q` | 검색어 (`/search` 와 같은 규칙) |
| `sort` | `latest`(기본: 작성일 최신 순), `reg_asc`, `date_desc`(발견일), `date_asc`. 그 밖의 값은 `latest`. 같은 날짜는 ID 로 다시 정렬 |
| `page` | 1부터. 0·음수·마지막보다 큰 값은 범위 안으로 맞춤. 한 페이지 15건 |

- 응답: 화면 `ItemList` (목록, 전체 건수, 페이지 번호, 조건을 유지한 정렬·페이지 링크)

### `GET /items/new`
- 응답: 화면 `registerForm`. 시간이 지나 보내지 못한 입력이 있으면 채워서 보여줌

### `POST /items/new` — `multipart/form-data`
| 파라미터 | 필수 | 규칙 |
|---|---|---|
| `foundDate` | ○ | `yyyy-MM-dd`, 2000-01-01 ~ 오늘 |
| `lineName`, `stationName` | ○ | 마스터 데이터에 있는 노선·역 조합 |
| `categoryName` | ○ | 마스터 데이터의 카테고리 |
| `itemName` | ○ | 100자 이내 |
| `contents` | ○ | 500자 이내 |
| `imageFile` | | 사진 (위 입력값 규칙) |
| `password` | 비회원만 | 반각 숫자 4자리 (BCrypt 로 저장) |

- 제목(`[날짜][노선][역][카테고리] 물건`)과 작성자명(회원은 닉네임, 비회원은 `匿名の天使 001` 형식의 번호)은 서버에서 만듭니다.
- 성공: `302 /items` · 입력 오류: 화면 `registerForm`(오류 문구, 입력값 유지) · 같은 내용 재전송: `302 /items` (저장하지 않음)

### `GET /items/{id}`
| 파라미터 | 설명 |
|---|---|
| `verify=edit\|delete` | JS 없이 「編集」「削除」를 누른 경우: 비밀번호 창을 연 화면 (비회원 글) |
| `reverify` | 수정을 보냈는데 비밀번호 확인이 풀려 있던 경우: 다시 확인하는 창 (입력한 내용은 보관됨) |
| `confirmDelete` | 삭제 확인 창을 연 화면 (권한이 있을 때만) |
| `allComments` | 모든 댓글 표시 (없으면 최근 50건 + 「以前のコメント(N件)をすべて表示」) |

- 응답: 화면 `itemDetail` · 없거나 삭제된 글: `404`
- 로그인하지 않고 보기만 하면 세션을 만들지 않습니다 (CSRF 토큰을 넣지 않음).

### `POST /items/{id}/check-password` — JSON
- 요청: 폼 `password`, 헤더 `X-CSRF-TOKEN`
- `200` `{"ok": true|false, "remaining": 남은 횟수, "lockMinutes": 5}` — 맞으면 이 세션을 「확인됨」으로 기록
- `429` `{"ok": false, "remaining": 0, "lockMinutes": 5}` — 연속으로 틀려서 잠김
- `404` (본문 없음) — 없는 글·삭제된 글·회원 글 (틀린 횟수로 세지 않음)
- `403` — CSRF 토큰 없음·불일치 (JS 는 토큰을 새로 받아 한 번 다시 보냄)

### `POST /items/{id}/verify`
JS 를 쓰지 않는 비밀번호 확인 (결과는 화면 이동).

| 파라미터 | 설명 |
|---|---|
| `action` | `edit`(기본) / `delete` |
| `password` | 4자리 비밀번호 |

- 맞음: `302 /items/{id}/edit` 또는 `302 /items/{id}?confirmDelete`
- 틀림·잠김: `302 /items/{id}?verify=edit|delete` + 안내 문구(flash `verifyError`, 남은 횟수 포함) · 없는 글: `404`

### `GET /items/{id}/edit`
- 권한 있음: 화면 `editForm` (보내지 못한 수정 내용이 있으면 채움)
- 권한 없음:
  - 회원 글이고 로그인하지 않음: `302 /login?required&next=/items/{id}/edit`
  - 비회원 글이고 보관된 수정 내용이 있음: `302 /items/{id}?reverify`
  - 그 밖: `302 /items/{id}`

### `POST /items/{id}/edit` — `multipart/form-data`
- 파라미터: `POST /items/new` 와 같음 (`password` 제외) + `isImageDeleted`(`true` 면 사진 삭제)
- 비회원 글인데 확인이 풀려 있으면 입력을 보관하고 `302 /items/{id}?reverify` (확인 후 수정 화면에 다시 채움). 회원 글을 본인이 아닌 사람이 보내면 `403`
- 성공: `302 /items/{id}` (비회원 글은 확인 상태를 지움 → 다시 수정하려면 다시 확인). 아무것도 바꾸지 않았으면 저장하지 않음(「編集済み」 표시 없음)
- 동시에 다른 요청이 글을 지웠거나 사진을 바꿨으면 반영하지 않고 안내 (조건부 UPDATE, 새로 저장한 사진 파일은 삭제)

### `POST /items/{id}/delete`
- 권한: 작성자 / 확인된 세션 (그 외 `403`)
- 논리 삭제(`DEL_FLG`)하고 사진 파일도 지움 → `302 /items`

## 3. 探しています 게시판

### `GET /lost-items`
- 파라미터: `GET /items` 와 같음. 역 필터 `station=unknown` 은 「駅不明」(역을 모르는 글)
- 응답: 화면 `lostList`

### `GET /lost-items/{id}`
- 파라미터: `confirmDelete`(작성자만), `allComments`
- 응답: 화면 `lostDetail` · 없거나 삭제된 글: `404`

### `GET /lost-items/new` (회원)
- 응답: 화면 `lostForm`. 처음에 작성 전 주의사항(사기·절도 경고) 창을 보여줌

### `POST /lost-items/new` (회원) — `multipart/form-data`
| 파라미터 | 필수 | 규칙 |
|---|---|---|
| `lostDate` | ○ | `yyyy-MM-dd`, 2000-01-01 ~ 오늘 |
| `lineName` | ○ | 마스터 데이터의 노선 |
| `stationName` | ○ | 그 노선의 역, 또는 `unknown`(駅がわからない → DB 에는 NULL) |
| `categoryName`, `itemName`, `contents`, `imageFile` | | 見つけた 와 같음 |
| `warningConfirmed` | ○ | `true` — 작성 전 주의사항을 확인했는지 (서버에서도 확인) |

- 성공: `302 /lost-items` · 입력 오류·주의사항 미확인: 화면 `lostForm`

### `GET /lost-items/{id}/edit` (회원)
- 작성자: 화면 `lostForm` · 작성자가 아님: `302 /lost-items/{id}`

### `POST /lost-items/{id}/edit` (작성자)
- 파라미터: 등록과 같음 + `isImageDeleted` · 작성자가 아님: `403` · 성공: `302 /lost-items/{id}`

### `POST /lost-items/{id}/resolve` (작성자)
- 파라미터: `resolved`(기본 `true`, `false` 면 해제) → `302 /lost-items/{id}`

### `POST /lost-items/{id}/delete` (작성자)
- 논리 삭제 + 사진 파일 삭제 → `302 /lost-items`

## 4. 댓글

두 게시판 공통. `{board}` 는 `items`(見つけた) 또는 `lost-items`(探しています).

### `GET /{board}/{id}/comments` (회원)
- 로그인하지 않은 사람이 댓글 영역의 「ログイン」을 누르면 오는 주소. 로그인 후 `302 /{board}/{id}#comments`
  (로그인이 끝나 보내지 못한 댓글이 있으면 `#commentForm` 으로 가서 입력 칸에 다시 채움)

### `POST /{board}/{id}/comments` (회원)
| 파라미터 | 규칙 |
|---|---|
| `contents` | 필수, 500자 이내 |

- 성공: `302 /{board}/{id}#comment-{commentId}` (등록한 댓글 위치)
- 입력 오류: `302 /{board}/{id}#commentForm` + flash `commentError`, `commentDraft` (입력 내용 유지)
- 없는 글·삭제된 글: `404` (확인과 저장을 `INSERT … SELECT` 한 문장으로 해서, 확인 직후 글이 삭제돼도 댓글이 들어가지 않음)
- 같은 내용 재전송: `302 /{board}/{id}#comments` (저장하지 않음)

### `POST /{board}/{id}/comments/{commentId}/delete` (작성자)
- 댓글을 쓴 본인만 (그 외 `403`), 그 글의 댓글이 아니면 `404` → `302 /{board}/{id}#comments`

## 5. 회원가입·로그인

### `GET /login`
| 파라미터 | 화면 안내 |
|---|---|
| `required` | 로그인이 필요한 화면에서 넘어옴 |
| `error` / `locked` | ID·비밀번호가 틀림 / 연속 실패로 잠김 |
| `registered` | 가입 완료 |
| `reauth` | 다른 곳에서 비밀번호를 바꿔 로그인이 끝남 |
| `next` | 로그인 후 돌아갈 화면 (헤더의 「ログイン」이 지금 화면 주소를 붙임) |

- 이미 로그인한 상태: `302 /`

### `POST /login` (Spring Security)
| 파라미터 | 설명 |
|---|---|
| `loginId`, `password` | ID·비밀번호 |
| `next` | 돌아갈 화면. 이 사이트 안의 경로만 사용 (`//`, `\`, 제어 문자, `/login`·`/logout` 은 무시) |

- 성공: `next` → 로그인이 필요해서 저장해 둔 화면 → `/` 순서로 `302`
- 실패: `302 /login?error[&next=…]` (입력한 ID·남은 횟수는 flash) · 잠김: `302 /login?locked` (잠긴 동안은 맞는 비밀번호도 확인하지 않음)

### `POST /logout` (Spring Security)
- `302 /?logout`. 세션 쿠키 `JSESSIONID` 삭제, `Clear-Site-Data: "cache"` (로그아웃 후 「뒤로」로 이전 화면이 보이지 않게)

### `GET /signup`
- 응답: 화면 `signup` · 로그인한 상태: `302 /`

### `POST /signup`
| 파라미터 | 규칙 |
|---|---|
| `loginId` | 반각 영숫자·`_` 4~20자. 대소문자만 다른 ID 도 중복으로 봄 (DB 의 `LOGINKEY` 유일 인덱스) |
| `password` / `passwordConfirm` | 영문자와 숫자를 포함한 8~64자 (반각) / 같은 값 |
| `realName` | 필수, 20자 이내 |
| `nickname` | 20자 이내, 비우면 `realName`. `匿名の天使`로 시작하거나 `退会したユーザー` 는 사용 불가 |
| `birthDate` | 과거 날짜, 1900-01-01 이후 |
| `phone` | 일본 전화번호 형식 (하이픈 있어도 없어도, 10~11자리) |
| `agree` | `true` (개인정보 취급 동의) |

- 성공: `302 /login?registered` · 오류: 화면 `signup` (입력값·오류 문구)

## 6. 마이페이지

모두 회원 전용.

### `GET /mypage`
| 파라미터 | 설명 |
|---|---|
| `tab` | `found` / `lost` / `comments`. 없으면 글·댓글이 있는 첫 번째 탭 |
| `page` | 한 페이지 10건 |

- 응답: 화면 `mypage` (회원정보, 탭별 건수, 목록). 댓글 링크는 모든 댓글을 보여주는 주소(`?allComments#comment-{id}`)

### `GET /mypage/edit` · `POST /mypage/edit`
- 파라미터: `realName`, `nickname`, `birthDate`, `phone` (가입과 같은 규칙)
- 성공: `302 /mypage?updated`. 닉네임을 바꾸면 지금까지 쓴 글·댓글의 작성자명도 한 트랜잭션으로 바꾸고, 다시 로그인하지 않아도 헤더에 반영

### `GET /mypage/password` · `POST /mypage/password`
- 파라미터: `currentPassword`, `newPassword`, `newPasswordConfirm` (현재와 같은 비밀번호는 불가)
- 성공: `302 /mypage?passwordChanged`. 다른 브라우저·기기의 로그인은 끝냄 (그쪽은 다음 요청 때 `/login?reauth`)

### `GET /mypage/withdraw` · `POST /mypage/withdraw`
- 파라미터: `currentPassword`, `agree=true`
- 성공: `302 /?withdrawn`. 개인정보(ID·비밀번호·氏名·生年月日·電話番号)를 지우고 글·댓글은 「退会したユーザー」로 남김. 모든 세션을 끝냄

## 7. 駅マップ

### `GET /map`
- 응답: 화면 `map` (Leaflet 지도 + 「駅を選ぶ」 목록. 지도를 쓰지 못해도 역을 골라 게시판으로 이동 가능)

### `GET /map/stations` — JSON
- 노선별 역 순서(노선도를 그릴 때 사용)와 역별 위치·두 게시판의 글 수. 아래 [예시](#get-mapstations) 참고

## 8. 유틸리티·정적 파일

### `GET /csrf` — JSON
- `{"headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token": "…"}`. 이 요청에서 세션이 생깁니다.

### 정적 파일 (엔드포인트 수에는 포함하지 않음)

| 경로 | 내용 | 캐시 |
|---|---|---|
| `/css/**`, `/js/**` | 화면 공통 스타일·스크립트. 화면에는 파일 내용의 해시를 붙인 주소(`mobile-<해시>.css`)로 나감 | 1년 (`public`) |
| `/webjars/**` | flatpickr 4.6.13, Leaflet 1.9.4 (프로젝트에 포함, 외부 CDN 을 쓰지 않음) | 1년 |
| `/logo/**` | 로고 (해시를 붙인 주소) | 1년 |
| `/images/**` | 업로드한 사진 (`jrfind.upload-dir`, 기본 `./uploads`). 파일명은 저장할 때마다 새로 만듦 | 1년 |
| `/h2-console/**` | H2 콘솔. 환경 변수 `JRFIND_H2_CONSOLE=true` 일 때만, localhost 에서만 | — |

- 응답 압축(gzip): HTML·CSS·JS·JSON, 1KB 이상

## JSON 엔드포인트 예시

### `POST /items/{id}/check-password`

```js
// 1) 토큰 받기 (처음 보낼 때 한 번)
const csrf = await (await fetch('/csrf', { headers: { Accept: 'application/json' } })).json();
// 2) 비밀번호 확인
const res = await fetch('/items/42/check-password', {
  method: 'POST',
  headers: { [csrf.headerName]: csrf.token },
  body: new URLSearchParams({ password: '1234' })
});
```

| 상황 | 상태 | 본문 |
|---|---|---|
| 맞음 | `200` | `{"ok":true,"remaining":5,"lockMinutes":5}` |
| 틀림 (2번째) | `200` | `{"ok":false,"remaining":3,"lockMinutes":5}` |
| 잠김 | `429` | `{"ok":false,"remaining":0,"lockMinutes":5}` |
| 없는 글·삭제된 글·회원 글 | `404` | (없음) |
| CSRF 토큰 없음·만료 | `403` | (없음) |

### `GET /csrf`

```json
{
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "kT0x…(요청마다 다른 값)"
}
```

### `GET /map/stations`

```json
{
  "lines": [
    { "name": "山手線", "stations": ["東京", "神田", "秋葉原", "上野", "…"] }
  ],
  "stations": [
    {
      "name": "新宿",
      "lat": 35.689729,
      "lng": 139.700464,
      "lines": ["山手線", "埼京線", "中央・総武緩行線"],
      "foundCount": 18,
      "lostCount": 0,
      "lostCenter": false
    }
  ]
}
```

- `lostCenter`: JR東日本 お忘れ物承り所가 있는 역 (東京, 大宮)
- 데이터: 6개 노선, 47개 역 (`STATION`, `STATION_LOCATION`). 노선·역·카테고리·역 위치는 실행 중에 바뀌지 않으므로 처음 한 번 읽은 것을 메모리에 둡니다.

## 보안 헤더

모든 화면 응답에 붙습니다 (`SecurityConfig`).

| 헤더 | 값 |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tile.openstreetmap.org; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Cache-Control` (화면) | `no-cache, no-store, max-age=0, must-revalidate` (Spring Security 기본값) |

- 스크립트는 모두 파일로 불러오므로 화면에 인라인 스크립트·`on…` 속성이 없습니다 (테스트가 모든 화면을 검사). 게시글 등에 스크립트가 끼어들어도 실행되지 않습니다.
- 세션 쿠키: `HttpOnly`, `SameSite=Lax`, URL 에 세션 ID 를 붙이지 않음 (`tracking-modes=cookie`)
