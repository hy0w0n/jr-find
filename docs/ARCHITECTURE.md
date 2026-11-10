# 시스템 구조·DB 설계

**한국어** | [日本語](ARCHITECTURE.ja.md) · [README](../README.md) · [API 명세](API.md)

## 목차

- [시스템 구조](#시스템-구조)
- [패키지와 역할](#패키지와-역할)
- [DB 설계 (ERD)](#db-설계-erd)
- [주요 요청 흐름](#주요-요청-흐름)
- [설계 판단](#설계-판단)

## 시스템 구조

서버에서 화면을 만들어 돌려주는 Spring MVC 애플리케이션입니다 (Spring Boot 4 · Java 21 · 내장 Tomcat). DB 는 파일로 저장하는 H2, DB 접근은 JdbcTemplate 으로 SQL 을 직접 씁니다.

```mermaid
flowchart TB
  B["브라우저<br/>HTML · JS"]
  OSM["OpenStreetMap<br/>지도 타일"]

  subgraph APP["Spring Boot 애플리케이션"]
    subgraph SEC["Spring Security 필터 체인"]
      HDR["보안 헤더<br/>CSP · Referrer-Policy · Permissions-Policy"]
      LAG["LoginAttemptGuard<br/>로그인 연속 실패 제한"]
      CSRF["CSRF 확인"]
      AUTHZ["접근 권한<br/>회원 전용 경로"]
      EFH["ExpiredFormHandler<br/>토큰 불일치 시 보던 화면으로, 입력 보관"]
    end

    subgraph WEB["controller"]
      CTRL["JrfindController · LostItemController<br/>CommentController · SearchController<br/>MemberController · MyPageController<br/>MapController · CsrfController"]
      SESS["세션 범위<br/>VerifiedItems · SubmittedForms · FormDraft"]
      VIEW["Thymeleaf 템플릿"]
    end

    subgraph SVC["service"]
      SV["JrfindService · LostItemService<br/>CommentService · MemberService · MapService"]
      LIM["PasswordAttemptLimiter<br/>시도 횟수 제한"]
      IMG["ImageStorage<br/>매직 넘버 확인 · 저장 · 삭제"]
      RES["ImageResizer<br/>축소 · EXIF 삭제"]
      ORP["OrphanImageCleaner<br/>매일 04:00"]
    end

    subgraph REPO["repository"]
      RP["Jdbc*Repository<br/>JdbcTemplate · KeywordCondition"]
      CACHE["CacheConfig<br/>노선 · 역 · 카테고리 메모리 캐시"]
      BF["SearchTextBackfill<br/>기동 시 검색용 컬럼 채우기"]
    end

    STATIC["정적 파일<br/>/css · /js 해시 주소<br/>/webjars flatpickr · Leaflet"]
  end

  DB[("H2 파일 DB<br/>./data/jrfinddb")]
  FS[("업로드 폴더<br/>./uploads")]

  B -->|"HTTP"| SEC
  SEC --> CTRL
  CTRL --> SESS
  CTRL --> SV
  CTRL --> VIEW
  VIEW -->|"HTML, gzip"| B
  SV --> LIM
  SV --> IMG
  IMG --> RES
  SV --> RP
  RP -.-> CACHE
  RP --> DB
  BF --> DB
  IMG --> FS
  ORP --> RP
  ORP --> FS
  B -->|"정적 파일 · /images, 1년 캐시"| STATIC
  STATIC -.-> FS
  B -->|"지도 타일"| OSM
```

- 화면의 스크립트는 모두 파일로 불러오고(`script-src 'self'`), 외부에서 받는 것은 지도 타일 이미지뿐입니다. flatpickr·Leaflet 은 WebJars 로 프로젝트에 포함했습니다.
- CSS·JS·로고 주소에는 파일 내용의 해시를 붙여(`mobile-<해시>.css`) 1년 동안 캐시하고, 파일을 고치면 주소가 바뀝니다.
- 실행 환경 설정: DB 주소 `SPRING_DATASOURCE_URL`, 업로드 폴더 `JRFIND_UPLOAD_DIR`, H2 콘솔 `JRFIND_H2_CONSOLE` (기본 꺼짐).

## 패키지와 역할

`com.example.jrfind`

| 패키지 | 클래스 | 역할 |
|---|---|---|
| `config` | `SecurityConfig` | 회원 전용 경로, 폼 로그인, 로그아웃, 세션 목록(SessionRegistry), CSP 등 보안 헤더 |
| | `LoginAttemptGuard` | `POST /login` 앞의 필터 + 성공·실패 처리: 잠금 확인, 실패 횟수 기록, 돌아갈 화면(`next`) |
| | `ExpiredFormHandler` | CSRF 토큰 불일치를 403 대신 보던 화면 + `?expired` 로, 입력은 `FormDraft` 로 보관 |
| | `WebMvcConfig` | `/images/**`(업로드 폴더), `/logo/**`(해시 주소) 경로와 캐시 |
| | `CacheConfig` | 노선·역·카테고리·역 위치를 메모리에 두는 캐시 (`@EnableCaching`) |
| `controller` | `JrfindController` · `LostItemController` | 두 게시판의 목록·상세·등록·수정·삭제, 비회원 글 비밀번호 확인 |
| | `CommentController` | 댓글 등록·삭제 (두 게시판 공통) |
| | `SearchController` · `MapController` · `MemberController` · `MyPageController` · `CsrfController` | 통합 검색, 駅マップ, 가입·로그인 화면, 마이페이지, CSRF 토큰 |
| | `VerifiedItems` · `SubmittedForms` (세션 범위), `FormDraft` | 비밀번호 확인을 통과한 글, 10분 안 같은 내용 재전송 막기, 보내지 못한 입력 보관 |
| | `BoardQuery` · `LoginMemberAdvice` · `FormErrors` · `SearchKeyword` | 조건을 유지한 목록 링크·페이지 번호, 화면 공통 로그인 정보, 오류 문구, 검색어 정리 |
| `domain` | `FoundItem` · `LostItem` · `Comment` · `Member` 등, `*Form` | 데이터와 입력 폼(Bean Validation), `Texts`(입력 문자열 정리), `Board`(게시판 종류) |
| `service` | `JrfindService` · `LostItemService` · `CommentService` · `MemberService` · `MapService` | 업무 처리: 마스터 데이터 확인, 제목 생성, 동시 변경 처리, 닉네임 변경·탈퇴(트랜잭션) |
| | `PasswordAttemptLimiter` | 비회원 글 비밀번호·로그인·현재 비밀번호의 연속 실패 잠금 (5분) |
| | `ImageStorage` · `ImageResizer` · `OrphanImageCleaner` | 사진 형식 확인·축소·저장·삭제, 쓰이지 않는 사진 정리 |
| `repository` | `Jdbc*Repository` (인터페이스 + JdbcTemplate 구현) | SQL. 조건부 UPDATE·DELETE, `INSERT … SELECT`, 목록 필터·정렬·페이지 |
| | `KeywordCondition` · `SearchTextBackfill` | 검색 조건과 검색용 값(NFKC·소문자), 비어 있는 검색용 컬럼 채우기 |

## DB 설계 (ERD)

테이블 7개 (`src/main/resources/schema.sql`). 실선은 외래 키, 점선은 외래 키 없이 애플리케이션에서 확인하는 관계입니다.

```mermaid
erDiagram
  MEMBER |o--o{ FOUND_ITEM : "회원 글 (비회원 글은 NULL)"
  MEMBER ||--o{ LOST_ITEM : "작성"
  MEMBER ||--o{ POST_COMMENT : "작성"
  FOUND_ITEM |o--o{ POST_COMMENT : "댓글"
  LOST_ITEM |o--o{ POST_COMMENT : "댓글"
  CATEGORY ||..o{ FOUND_ITEM : "CATEGORYNAME"
  CATEGORY ||..o{ LOST_ITEM : "CATEGORYNAME"
  STATION ||..o{ FOUND_ITEM : "LINENAME + STATIONNAME"
  STATION |o..o{ LOST_ITEM : "역 모름이면 NULL"
  STATION_LOCATION ||..|{ STATION : "STATIONNAME"

  MEMBER {
    BIGINT ID PK
    VARCHAR LOGINID UK "20, 탈퇴하면 NULL"
    VARCHAR PASSWORD "100, BCrypt"
    VARCHAR REALNAME "20"
    VARCHAR NICKNAME "20, NOT NULL"
    DATE BIRTHDATE
    VARCHAR PHONE "20"
    TIMESTAMP AGREEDDATE "NOT NULL, 개인정보 동의"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    TIMESTAMP WITHDRAWNDATE "탈퇴 일시"
    VARCHAR LOGINKEY UK "생성 컬럼 LOWER(LOGINID)"
  }
  FOUND_ITEM {
    BIGINT ID PK
    VARCHAR TITLE "200, 서버에서 생성"
    DATE FOUNDDATE
    VARCHAR LINENAME "50"
    VARCHAR STATIONNAME "50"
    VARCHAR CATEGORYNAME "50"
    VARCHAR ITEMNAME "100"
    VARCHAR CONTENTS "500"
    VARCHAR IMGPATH "200, 사진 없으면 NULL"
    VARCHAR PASSWORD "100, 비회원 글만 BCrypt"
    VARCHAR AUTHOR "20"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    BOOLEAN DEL_FLG "논리 삭제"
    TIMESTAMP MODIFIEDDATE
    BIGINT MEMBERID FK "비회원 글은 NULL"
    VARCHAR SEARCHTEXT "검색용, 제목+본문 NFKC 소문자"
  }
  LOST_ITEM {
    BIGINT ID PK
    VARCHAR TITLE "200"
    DATE LOSTDATE
    VARCHAR LINENAME "50"
    VARCHAR STATIONNAME "50, 역 모름이면 NULL"
    VARCHAR CATEGORYNAME "50"
    VARCHAR ITEMNAME "100"
    VARCHAR CONTENTS "500"
    VARCHAR IMGPATH "200"
    VARCHAR AUTHOR "20, 작성 시점의 닉네임"
    BIGINT MEMBERID FK "NOT NULL"
    BOOLEAN RESOLVED "찾음 표시"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    BOOLEAN DEL_FLG "논리 삭제"
    TIMESTAMP MODIFIEDDATE
    VARCHAR SEARCHTEXT "검색용"
  }
  POST_COMMENT {
    BIGINT ID PK
    BIGINT FOUNDITEMID FK "둘 중 하나만"
    BIGINT LOSTITEMID FK "둘 중 하나만"
    VARCHAR CONTENTS "500"
    VARCHAR AUTHOR "20"
    BIGINT MEMBERID FK "NOT NULL"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    BOOLEAN DEL_FLG "논리 삭제"
  }
  CATEGORY {
    BIGINT ID PK
    VARCHAR CATEGORYNAME UK "20"
  }
  STATION {
    BIGINT ID PK
    VARCHAR LINENAME "50, UNIQUE(LINENAME, STATIONNAME)"
    VARCHAR STATIONNAME "50"
  }
  STATION_LOCATION {
    VARCHAR STATIONNAME PK "50"
    DECIMAL LATITUDE "9,6"
    DECIMAL LONGITUDE "9,6"
  }
```

### 제약 조건

| 테이블 | 제약 | 내용 |
|---|---|---|
| `MEMBER` | `CK_MEMBER_WITHDRAWN` (CHECK) | 탈퇴하지 않은 회원은 ID·비밀번호·氏名·生年月日·電話番号가 모두 있고, 탈퇴한 회원(`WITHDRAWNDATE` 있음)은 모두 NULL |
| `MEMBER` | `LOGINKEY` 생성 컬럼 + `UK_MEMBER_LOGINKEY` | `LOWER(LOGINID)` 에 유일 인덱스 → `Taro` 와 `taro` 가 동시에 가입해도 DB 에서 막음. 탈퇴 회원은 NULL 이라 여러 명 가능 |
| `MEMBER` | `LOGINID` UNIQUE | 탈퇴하면 NULL (같은 ID 로 다시 가입 가능) |
| `POST_COMMENT` | CHECK `(FOUNDITEMID IS NULL) <> (LOSTITEMID IS NULL)` | 댓글은 두 게시판 중 정확히 한 글에만 연결 |
| `CATEGORY` | `CATEGORYNAME` UNIQUE | |
| `STATION` | UNIQUE `(LINENAME, STATIONNAME)` | 같은 역이 여러 노선에 있으면 노선마다 한 행 |

### 인덱스

| 이름 | 대상 | 용도 |
|---|---|---|
| `IX_FOUND_ITEM_REGDATE` | `FOUND_ITEM(REGISTRATIONDATE, ID)` | 작성일 순 목록 (기본 정렬) |
| `IX_FOUND_ITEM_FOUNDDATE` | `FOUND_ITEM(FOUNDDATE, ID)` | 발견일 순 목록 |
| `IX_LOST_ITEM_REGDATE` | `LOST_ITEM(REGISTRATIONDATE, ID)` | 작성일 순 목록 |
| `IX_LOST_ITEM_LOSTDATE` | `LOST_ITEM(LOSTDATE, ID)` | 분실일 순 목록 |
| `UK_MEMBER_LOGINKEY` | `MEMBER(LOGINKEY)` | 대소문자를 구분하지 않는 ID 유일성 |

- 외래 키 컬럼(`MEMBERID`, `FOUNDITEMID`, `LOSTITEMID`)에는 H2 가 인덱스를 자동으로 만듭니다 (목록의 댓글 수, 마이페이지).
- 마스터 데이터(`data.sql`, 기동할 때마다 `MERGE`): 카테고리 11, 노선·역 78행(6개 노선), 역 위치 47.
- 스키마는 기동할 때마다 `CREATE … IF NOT EXISTS` / `ALTER … IF NOT EXISTS` 로 반영되므로, 예전 DB 도 그대로 새 구조로 올라갑니다.

## 주요 요청 흐름

### 1. 비회원 글 수정 (4자리 비밀번호 확인 → 세션에 기록 → 조건부 UPDATE)

```mermaid
sequenceDiagram
  autonumber
  participant U as 브라우저
  participant K as CsrfController
  participant C as JrfindController
  participant V as VerifiedItems (세션)
  participant S as JrfindService
  participant L as PasswordAttemptLimiter
  participant R as JdbcJrfindRepository

  U->>K: GET /csrf (처음 보낼 때 한 번)
  K-->>U: headerName, token (이때 세션 생성)
  U->>C: POST /items/{id}/check-password (password, X-CSRF-TOKEN)
  C->>S: checkPassword(id, password, id@접속IP)
  S->>R: findPasswordHash(id)
  alt 없는 글 · 삭제된 글 · 회원 글
    C-->>U: 404 (틀린 횟수로 세지 않음)
  else 잠김 (5회 실패 후 5분)
    C-->>U: 429 ok=false, remaining=0
  else 확인
    S->>S: NFKC 로 맞춤, 4자리 숫자가 아니면 세지 않음
    S->>L: 맞으면 reset, 틀리면 recordFailure
    C->>V: add(id) (맞은 경우)
    C-->>U: 200 ok, remaining, lockMinutes
  end
  U->>C: GET /items/{id}/edit
  C->>V: contains(id)
  C-->>U: 수정 화면
  U->>C: POST /items/{id}/edit
  C->>V: contains(id) (풀려 있으면 입력 보관 후 302 ?reverify)
  C->>S: updateItem(id, form, file, isImageDeleted)
  S->>R: update(item, 읽었을 때의 사진)
  Note over R: UPDATE … WHERE ID=? AND DEL_FLG=FALSE<br/>AND ImgPath IS NOT DISTINCT FROM ?
  alt 0행 (그사이 삭제되었거나 사진이 바뀜)
    S-->>C: InvalidPostException (안내 문구)
    C-->>U: 수정 화면 + 안내
  else 1행
    C->>V: remove(id) (다시 수정하려면 다시 확인)
    C-->>U: 302 /items/{id}
  end
```

### 2. 회원 로그인 (연속 실패 제한)

```mermaid
sequenceDiagram
  autonumber
  participant U as 브라우저
  participant G as LoginAttemptGuard (필터)
  participant A as Spring Security 인증
  participant M as MemberService
  participant L as PasswordAttemptLimiter

  U->>G: POST /login (loginId, password, next, _csrf)
  G->>M: isLoginLocked(loginId, 접속IP)
  M->>L: ID+IP 5회, IP 20회 기준
  alt 잠김
    G-->>U: 302 /login?locked (비밀번호를 확인하지 않음)
  else 잠기지 않음
    G->>A: 다음 필터로
    A->>M: loadUserByUsername (ID 앞뒤 공백 정리) + BCrypt 비교
    alt 성공
      A->>G: onAuthenticationSuccess
      G->>M: recordLoginSuccess (그 ID+IP 횟수 삭제, IP 횟수는 유지)
      G-->>U: 302 next 또는 저장된 화면 또는 /
    else ID · 비밀번호가 틀림
      A->>G: onAuthenticationFailure
      G->>M: recordLoginFailure (없는 ID 도 똑같이 셈)
      G-->>U: 302 /login?error 또는 ?locked (입력한 ID, 남은 횟수는 flash)
    end
  end
```

### 3. 사진을 바꾼 수정 (형식 확인 → 축소·EXIF 삭제 → 저장 → 조건부 UPDATE → 이전 파일 삭제)

```mermaid
sequenceDiagram
  autonumber
  participant C as Controller
  participant S as Service
  participant I as ImageStorage
  participant Z as ImageResizer
  participant FS as 업로드 폴더
  participant R as Repository

  C->>S: updateItem(id, form, imageFile)
  S->>S: 노선 · 역 · 카테고리 실존, 날짜 범위 확인
  S->>I: store(imageFile)
  I->>I: 파일 앞부분(매직 넘버)으로 JPG · PNG · GIF · WEBP 판별
  opt 이미지가 아님
    I-->>C: InvalidPostException
  end
  I->>Z: process(bytes, 확장자)
  Z->>Z: 크기만 먼저 읽고 5,000만 화소 초과면 거부
  Z->>Z: 휴대폰 회전 정보 반영, 긴 변 1,280px 로 축소
  Z->>Z: JPG 는 메타데이터 없이 다시 저장 (EXIF · GPS 삭제)
  I->>FS: yyyyMMddHHmmss_8자리.jpg 로 저장
  S->>R: update(item, 읽었을 때의 사진)
  alt 0행 (동시에 삭제 · 변경됨)
    S->>I: delete(새 파일)
    S-->>C: InvalidPostException (안내)
  else 1행
    S->>I: delete(이전 파일) (DB 반영 후에 삭제)
  end
  Note over FS: 그래도 남은 파일은 OrphanImageCleaner 가<br/>매일 04:00, 1시간 이상 지난 것만 정리
```

## 설계 판단

| 주제 | 선택 | 이유 |
|---|---|---|
| DB 접근 | JdbcTemplate (JPA 를 쓰지 않음) | 실습에서 익힌 방식이고 테이블이 7개로 작습니다. 조건부 UPDATE, `INSERT … SELECT`, LIKE 이스케이프처럼 실행되는 SQL 을 그대로 보고 제어해야 하는 곳이 많습니다. |
| 동시 수정·삭제 | 조건부 UPDATE·DELETE | `WHERE DEL_FLG = FALSE AND ImgPath IS NOT DISTINCT FROM ?` 로 "읽었을 때와 그대로인 경우만" 반영합니다. 늦은 쪽은 안내하고 새로 저장한 사진은 지워, 지운 글에 사진이 공개된 채 남거나 쓰이지 않는 파일이 쌓이지 않게 했습니다. 삭제는 사진이 바뀌면 다시 읽어 최대 5번 시도합니다. |
| 댓글 저장 | `INSERT … SELECT … WHERE DEL_FLG = FALSE` | 글이 있는지 확인과 저장을 한 문장으로 해서, 확인 직후 글이 삭제돼도 댓글이 들어가지 않습니다. |
| 검색 | 검색용 컬럼 `SEARCHTEXT` | 저장할 때 제목+본문을 NFKC·소문자로 맞춰 두고, 검색어도 같은 방법으로 맞춰 한 컬럼만 비교합니다. 예전에는 검색할 때마다 모든 글을 `LOWER(TRANSLATE(…))` 로 변환해 글 1만 건에서 가장 느린 검색이 약 2.9초였고, 지금은 수십 ms 입니다 ([README 성능](../README.md#성능)). |
| 목록 정렬 | `(작성일·발견일·분실일, ID)` 인덱스 | 인덱스 순서대로 앞에서 한 페이지만 읽습니다. 없으면 모든 글의 댓글 수를 센 뒤 정렬해야 했습니다. 같은 날짜의 글이 페이지마다 섞이지 않도록 ID 를 함께 씁니다. |
| 마스터 데이터 | `@Cacheable` (메모리) | 노선·역·카테고리·역 위치는 기동할 때 `data.sql` 로만 바뀌므로 처음 한 번만 읽습니다. 바꿀 수 없는 List 로 돌려줍니다. |
| 세션 | 로그인하지 않고 보기만 하면 만들지 않음 | CSRF 토큰을 만들면 세션이 생기므로 열람 화면에는 넣지 않고, JS 가 보낼 때 `GET /csrf` 로 받습니다. 방문자·검색 로봇마다 서버 메모리에 세션이 쌓이지 않습니다. |
| 비회원 글 권한 | 세션 범위 `VerifiedItems` | 비밀번호 확인 결과를 화면(JS)이 아니라 서버 세션에 기록하고, 수정·삭제 요청마다 서버가 확인합니다. 수정이 끝나면 지워 다시 확인하게 합니다. |
| 사진 | 형식 확인 + 축소 + EXIF 삭제 | 확장자·Content-Type 은 바꿀 수 있으므로 파일 앞부분으로 판별합니다. 휴대폰 사진의 촬영 위치(GPS)가 공개되지 않도록 JPG 는 항상 다시 저장합니다. |
| 화면 스크립트 | 모두 파일, CSP `script-src 'self'` | 인라인 스크립트·`on…` 속성을 없애 게시글에 스크립트가 끼어들어도 실행되지 않게 했습니다. 외부 라이브러리는 WebJars 로 포함했습니다. |
