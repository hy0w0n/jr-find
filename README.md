# JR Find

도쿄 JR 역에서 주운 분실물을 공유하는 게시판 웹 애플리케이션입니다. (화면 언어: 일본어)

로그인 없이 글을 올릴 수 있고, 작성할 때 정한 4자리 비밀번호로 수정·삭제합니다.

## 기술 스택

- Java 21, Spring Boot 4.0 (Spring MVC, JDBC, Validation)
- Thymeleaf, HTML / CSS / JavaScript, flatpickr
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
- 작성자 이름 자동 부여 (`匿名の天使 001` 형식)
- 비밀번호 확인 후 수정·삭제 (삭제는 논리 삭제)

## 보안 처리

- 비밀번호는 BCrypt 해시로 저장하고, 화면(HTML)에는 내보내지 않음
- 수정·삭제는 서버에서 "비밀번호 확인을 통과한 세션인지" 검사 (화면 JS 검사만으로 막지 않음)
- 비밀번호 5회 연속 실패 시 5분간 잠금 (4자리 비밀번호 무차별 대입 방지)
- 업로드 파일은 파일 앞부분(매직 넘버)으로 실제 이미지인지 확인하고, 원본 파일명 대신 서버에서 만든 이름으로 저장 (경로 조작 방지)
- 입력값은 서버에서도 다시 검증 (Bean Validation + 노선·역·카테고리 실존 여부)
- 세션 쿠키 `HttpOnly`, `SameSite=Lax`, URL에 세션 ID 노출 안 함

## 폴더 구조

```
src/main/java/com/example/jrfind
├─ JrFindApplication.java
├─ config/       WebMvcConfig (업로드 이미지·로고 경로 설정)
├─ controller/   JrfindController, VerifiedItems (비밀번호 확인 상태 · 세션)
├─ domain/       FoundItem, FoundItemForm, FoundItemCreateForm, Category, Station
├─ repository/   JrfindRepository, JdbcJrfindRepository (JdbcTemplate)
└─ service/      JrfindService, ImageStorage, PasswordAttemptLimiter
src/main/resources
├─ schema.sql, data.sql
├─ templates/    home, ItemList, itemDetail, registerForm, editForm, error, fragments/header
└─ logo/
```

## 앞으로 추가할 기능

- 探しています(분실물을 찾는) 게시판
- 회원가입·로그인, 마이페이지
- 댓글
- 검색
- 지도·노선도, 분실물 센터 안내
