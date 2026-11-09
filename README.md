# JR Find

도쿄 JR 역의 분실물 게시판 웹 애플리케이션입니다. (화면 언어: 일본어)

- **見つけた** 게시판: 주운 물건을 알리는 게시판. 로그인 없이도 글을 올릴 수 있고(작성할 때 정한 4자리 비밀번호로 수정·삭제), 회원은 로그인한 계정으로 글을 올리고 비밀번호 없이 수정·삭제합니다.
- **探しています** 게시판: 잃어버린 물건을 찾는 게시판. 회원만 글을 쓸 수 있습니다.
- 두 게시판 모두 회원은 댓글을 달 수 있습니다. (감사 인사, "분실물 센터에 맡겼어요" 같은 후속 소식)

## 기술 스택

- Java 21, Spring Boot 4.0 (Spring MVC, Spring Security, JDBC, Validation)
- Thymeleaf, HTML / CSS / JavaScript, flatpickr, Leaflet + OpenStreetMap (駅マップ)
- H2 Database (파일 모드)
- Maven

## 실행 방법

Java 21이 필요합니다.

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

브라우저에서 http://localhost:8080 에 접속합니다.

- 처음 실행하면 `data/` 폴더에 H2 DB가 만들어지고, `schema.sql`·`data.sql`로 테이블과 노선·역·카테고리 데이터가 들어갑니다.
- 업로드한 이미지는 `uploads/` 폴더에 저장됩니다.
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/jrfinddb`, 사용자 `sa`, 비밀번호 없음)

설정은 환경변수로 바꿀 수 있습니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./data/jrfinddb;AUTO_SERVER=TRUE` | DB 접속 URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `sa` / (없음) | DB 계정 |
| `JRFIND_UPLOAD_DIR` | `./uploads` | 이미지 저장 폴더 |

테스트: `./mvnw test` (메모리 DB 사용)

## 주요 기능 (見つけた 게시판)

- 게시글 목록: 노선·역·카테고리 필터, 발견일·작성일 정렬, 페이지네이션 (15건씩)
- 게시글 등록: 달력으로 발견일 선택, 노선에 따라 역 목록 변경, 제목 자동 생성, 사진 첨부
- 비회원 글: 작성자 이름 자동 부여 (`匿名の天使 001` 형식), 4자리 비밀번호 확인 후 수정·삭제
- 회원 글: 닉네임으로 표시, 작성자 본인만 수정·삭제 (삭제는 모두 논리 삭제)

## 探しています 게시판

- 목록·상세는 누구나 볼 수 있고, 작성·수정·삭제는 회원만 가능 (로그인 전이면 안내 문구와 함께 로그인 화면으로 이동하고, 로그인 후 원래 화면으로 돌아옴)
- 글쓰기 전에 주의사항(사기·절도 경고)을 확인해야 작성 가능 (서버에서도 확인 여부 검사)
- 잃어버린 역을 모르면 「駅がわからない」 선택 가능 (제목에는 `駅不明`으로 표시)
- 작성자는 물건을 찾으면 「見つかりました」로 표시 (목록·상세에 표시, 해제 가능)
- 목록 필터·정렬·페이지네이션, 사진 첨부는 見つけた 게시판과 같음
- 입력 오류로 화면이 다시 표시돼도 입력값 유지

## 댓글 (두 게시판 공통)

- 댓글은 누구나 볼 수 있고, 작성은 회원만 가능 (로그인 전이면 「ログイン」 링크 → 로그인 후 그 게시글의 댓글 위치로 돌아옴)
- 댓글을 쓴 본인만 삭제 가능 (논리 삭제). 게시글 작성자가 쓴 댓글에는 「投稿者」 표시
- 최대 500자, 빈 댓글은 화면과 서버 양쪽에서 막고, 오류가 나도 입력한 내용은 유지
- 목록 화면의 물건 이름 옆에 댓글 수 표시
- 댓글 테이블 하나를 두 게시판이 같이 사용 (`FOUNDITEMID` / `LOSTITEMID` 중 하나에만 값이 들어가도록 CHECK 제약)

## 검색

- 헤더·홈 화면의 검색창 → 통합 검색 화면 (`/search?q=`): 두 게시판에서 최신 5건씩과 전체 건수, 「すべて見る」로 각 게시판 목록 이동
- 게시판 목록에서는 검색어(`?q=`)와 노선·역·카테고리 필터, 정렬, 페이지 이동을 함께 사용 (「検索をやめる」로 검색어만 해제)
- 제목(날짜·노선·역·카테고리·물건 이름 포함)과 본문에서 찾음. 공백(전각 포함)으로 나눈 단어는 모두 포함하는 글만 (최대 5단어)
- 대소문자 구분 없음, `%` `_` 는 와일드카드가 아니라 글자 그대로 검색 (LIKE 이스케이프)

## 회원가입·로그인

- 회원가입: ID, 비밀번호, 氏名, ニックネーム(선택, 비우면 氏名), 生年月日, 電話番号, 개인정보 취급 동의
- 입력 오류는 항목별로 표시하고 입력값은 유지 (ID 중복, 비밀번호 확인 불일치 포함)
- 로그인·로그아웃은 Spring Security 폼 로그인 사용. 로그인 실패 시 ID와 비밀번호 중 무엇이 틀렸는지 구분하지 않음
- 연속으로 틀리면 잠시 로그인을 제한하고 안내 문구 표시 (기준은 아래 「보안 처리」)

## 마이페이지 (`/mypage`, 회원만)

- 헤더의 유저 아이콘·닉네임으로 이동. 내 회원정보와 내가 쓴 글(見つけた / 探しています)·댓글을 탭별로 10건씩 표시
- 댓글 탭에서 누르면 그 게시글의 해당 댓글 위치로 이동 (삭제된 게시글의 댓글은 제외)
- 회원정보 변경 (氏名・ニックネーム・生年月日・電話番号, ID는 변경 불가): 닉네임을 바꾸면 지금까지 쓴 글·댓글의 작성자명도 함께 변경 (트랜잭션), 다시 로그인하지 않아도 헤더에 바로 반영
- 비밀번호 변경: 현재 비밀번호 확인 (5회 연속 실패 시 5분간 잠금), 새 비밀번호 규칙·확인 일치 검사
- 退会 (`/mypage/withdraw`): 비밀번호 재확인 + 주의 사항 확인 체크 + 확인 대화상자
  - 회원정보(ID·비밀번호·氏名・生年月日・電話番号)는 삭제하고, 글·댓글은 남겨 작성자명을 「退会したユーザー」로 변경 (트랜잭션)
  - 탈퇴 화면에 남아 있는 내 글·댓글 수를 보여 줘서, 지우고 싶은 글은 탈퇴 전에 지울 수 있게 함
  - 다른 브라우저·기기에 로그인해 둔 세션도 함께 종료 (Spring Security `SessionRegistry`)
  - 같은 ID 로 다시 가입할 수 있지만 예전 글·댓글과는 연결되지 않음 (글·댓글은 회원 번호로 연결)
  - 「退会したユーザー」는 닉네임으로 쓸 수 없음 (「匿名の天使」로 시작하는 이름과 같은 처리)
  - DB 에서도 CHECK 제약으로 "탈퇴하지 않은 회원은 개인정보가 모두 있고, 탈퇴한 회원은 모두 비어 있음"을 보장

## 駅マップ (`/map`)

- 헤더·홈 화면의 지도 아이콘으로 이동. Leaflet + OpenStreetMap 지도 (API 키 불필요)
- 역마다 마커 표시 (게시글이 있는 역은 빨간색), 역을 누르면 지나는 노선과 見つけた / 探しています 게시글 수, 해당 역으로 거른 게시판 목록 링크
- 노선도: 노선별 색으로 역을 순서대로 이은 선 (범례 체크박스로 노선별 표시·숨김), 「全体を表示」로 전체 노선 범위 보기
- 역 위치는 `STATION_LOCATION` 테이블 (`data.sql`, HeartRails Express API 에서 조회한 좌표), 지도 데이터는 `/map/stations` (JSON)
- お忘れ物 문의처 안내 (JR東日本 お忘れ物問い合わせ 번호·受付時間, 東京駅・大宮駅 お忘れ物承り所): 지도와 두 상세 화면 사이드바에 공통 표시. 2026년 11월 JR東日本 공식 사이트 기준이며 공식 페이지 링크를 함께 둠

## 보안 처리

- 비밀번호(회원·비회원 글 모두)는 BCrypt 해시로 저장하고, 화면(HTML)에는 내보내지 않음
- 모든 POST 요청에 CSRF 토큰 확인 (폼은 hidden 값, fetch 는 헤더로 전송)
- 수정·삭제 권한은 서버에서 검사: 회원 글은 작성자 본인, 비회원 글은 "비밀번호 확인을 통과한 세션", 댓글은 쓴 본인 (화면 JS 검사만으로 막지 않음)
- 비밀번호 5회 연속 실패 시 5분간 잠금 (4자리 비밀번호 무차별 대입 방지)
- 로그인 연속 실패 제한 (잠긴 동안은 맞는 비밀번호도 확인하지 않음)
  - 같은 접속 IP 에서 같은 ID 로 5회 틀리면 그 IP 에서 그 ID 의 로그인을 5분간 막음
  - ID 를 바꿔 가며 틀려도 같은 IP 에서 20회가 되면 그 IP 의 로그인을 5분간 막음 (자기 계정에 로그인해도 이 횟수는 지워지지 않음)
  - ID 만으로 잠그지 않고 접속 IP 와 묶어서 세므로, 다른 사람이 일부러 틀려도 다른 곳의 주인은 계속 로그인할 수 있음
  - 없는 ID 도 똑같이 세고 똑같이 잠그므로 잠금 여부로 ID 가 있는지 알 수 없음
- 업로드 파일은 파일 앞부분(매직 넘버)으로 실제 이미지인지 확인하고, 원본 파일명 대신 서버에서 만든 이름으로 저장 (경로 조작 방지)
- 사진을 바꾸거나 지우면 이전 이미지 파일을, 게시글을 삭제하면 그 글의 이미지 파일을 업로드 폴더에서 삭제
  (삭제한 글의 사진이 `/images/파일명` 주소로 계속 보이지 않도록. DB 반영 후에 지우고, 업로드 폴더 밖의 파일은 지우지 않음)
- 입력값은 서버에서도 다시 검증 (Bean Validation + 노선·역·카테고리 실존 여부)
- 세션 쿠키 `HttpOnly`, `SameSite=Lax`, URL에 세션 ID 노출 안 함

## 폴더 구조

```
src/main/java/com/example/jrfind
├─ JrFindApplication.java
├─ config/       SecurityConfig (로그인·CSRF), LoginAttemptGuard (로그인 연속 실패 제한),
│                WebMvcConfig (업로드 이미지·로고 경로)
├─ controller/   JrfindController (見つけた), LostItemController (探しています), CommentController (댓글),
│                SearchController (통합 검색), MemberController, MyPageController (마이페이지),
│                MapController (駅マップ), LoginMemberAdvice (헤더용 로그인 정보),
│                VerifiedItems (비밀번호 확인 상태 · 세션)
├─ domain/       FoundItem, FoundItemForm, LostItem, LostItemForm, Comment, CommentForm, Board,
│                Member, SignupForm, ProfileForm, PasswordForm, WithdrawForm, LoginMember, Category, Station,
│                StationLocation, MapData
├─ repository/   JrfindRepository, LostItemRepository, CommentRepository, MemberRepository (+ JdbcTemplate 구현),
│                KeywordCondition (검색어 SQL 조건)
└─ service/      JrfindService, LostItemService, CommentService, MemberService, MapService,
                 ImageStorage, PasswordAttemptLimiter
src/main/resources
├─ schema.sql, data.sql
├─ templates/    home, ItemList, itemDetail, registerForm, editForm (見つけた)
│                lostList, lostDetail, lostForm (探しています), search, login, signup,
│                mypage, profileEdit, passwordChange, withdraw (마이페이지), map (駅マップ), error,
│                fragments/header, fragments/comments (두 상세 화면 공통 댓글 영역),
│                fragments/lostCenter (お忘れ物 문의처)
└─ logo/
```

## 앞으로 개선할 점

- ID·비밀번호 찾기 (회원 정보에 이메일이 없어 본인 확인 수단을 먼저 마련해야 함)
- 기획 단계의 발전 기능: 1:1 채팅, 역 인근 위치 선택, 사진 여러 장 첨부
