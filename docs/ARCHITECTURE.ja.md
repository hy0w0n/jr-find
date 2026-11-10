# システム構成・DB 設計

[한국어](ARCHITECTURE.md) | **日本語** · [README](../README.ja.md) · [API 仕様](API.ja.md)

## 目次

- [システム構成](#システム構成)
- [パッケージと役割](#パッケージと役割)
- [DB 設計（ER 図）](#db-設計er-図)
- [主なリクエストの流れ](#主なリクエストの流れ)
- [設計上の判断](#設計上の判断)

## システム構成

サーバーで画面を生成して返す Spring MVC アプリケーションです（Spring Boot 4・Java 21・組み込み Tomcat）。DB はファイルに保存する H2、DB へのアクセスは JdbcTemplate で SQL を直接書いています。

```mermaid
flowchart TB
  B["ブラウザ<br/>HTML · JS"]
  OSM["OpenStreetMap<br/>地図タイル"]

  subgraph APP["Spring Boot アプリケーション"]
    subgraph SEC["Spring Security フィルターチェーン"]
      HDR["セキュリティヘッダー<br/>CSP · Referrer-Policy · Permissions-Policy"]
      LAG["LoginAttemptGuard<br/>ログインの連続失敗制限"]
      CSRF["CSRF の確認"]
      AUTHZ["アクセス権限<br/>会員専用のパス"]
      EFH["ExpiredFormHandler<br/>トークン不一致なら元の画面へ、入力を保管"]
    end

    subgraph WEB["controller"]
      CTRL["JrfindController · LostItemController<br/>CommentController · SearchController<br/>MemberController · MyPageController<br/>MapController · CsrfController"]
      SESS["セッションスコープ<br/>VerifiedItems · SubmittedForms · FormDraft"]
      VIEW["Thymeleaf テンプレート"]
    end

    subgraph SVC["service"]
      SV["JrfindService · LostItemService<br/>CommentService · MemberService · MapService"]
      LIM["PasswordAttemptLimiter<br/>試行回数の制限"]
      IMG["ImageStorage<br/>マジックナンバー確認 · 保存 · 削除"]
      RES["ImageResizer<br/>縮小 · EXIF 削除"]
      ORP["OrphanImageCleaner<br/>毎日 04:00"]
    end

    subgraph REPO["repository"]
      RP["Jdbc*Repository<br/>JdbcTemplate · KeywordCondition"]
      CACHE["CacheConfig<br/>路線 · 駅 · カテゴリのメモリキャッシュ"]
      BF["SearchTextBackfill<br/>起動時に検索用カラムを埋める"]
    end

    STATIC["静的ファイル<br/>/css · /js ハッシュ付きアドレス<br/>/webjars flatpickr · Leaflet"]
  end

  DB[("H2 ファイル DB<br/>./data/jrfinddb")]
  FS[("アップロードフォルダ<br/>./uploads")]

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
  B -->|"静的ファイル · /images, 1年キャッシュ"| STATIC
  STATIC -.-> FS
  B -->|"地図タイル"| OSM
```

- 画面のスクリプトはすべてファイルで読み込み（`script-src 'self'`）、外部から取得するのは地図タイルの画像だけです。flatpickr・Leaflet は WebJars でプロジェクトに同梱しています。
- CSS・JS・ロゴのアドレスにはファイル内容のハッシュを付け（`mobile-<ハッシュ>.css`）1年間キャッシュし、ファイルを変更するとアドレスが変わります。
- 実行環境の設定：DB のアドレス `SPRING_DATASOURCE_URL`、アップロードフォルダ `JRFIND_UPLOAD_DIR`、H2 コンソール `JRFIND_H2_CONSOLE`（既定はオフ）。

## パッケージと役割

`com.example.jrfind`

| パッケージ | クラス | 役割 |
|---|---|---|
| `config` | `SecurityConfig` | 会員専用のパス、フォームログイン、ログアウト、セッション一覧（SessionRegistry）、CSP などのセキュリティヘッダー |
| | `LoginAttemptGuard` | `POST /login` の前のフィルター＋成功・失敗の処理：ロックの確認、失敗回数の記録、戻り先（`next`） |
| | `ExpiredFormHandler` | CSRF トークンの不一致を、403 ではなく元の画面＋`?expired` へ。入力は `FormDraft` に保管 |
| | `WebMvcConfig` | `/images/**`（アップロードフォルダ）、`/logo/**`（ハッシュ付きアドレス）のパスとキャッシュ |
| | `CacheConfig` | 路線・駅・カテゴリ・駅の位置をメモリに保持するキャッシュ（`@EnableCaching`） |
| `controller` | `JrfindController` · `LostItemController` | 両掲示板の一覧・詳細・登録・編集・削除、非会員の投稿のパスワード確認 |
| | `CommentController` | コメントの登録・削除（両掲示板で共通） |
| | `SearchController` · `MapController` · `MemberController` · `MyPageController` · `CsrfController` | 統合検索、駅マップ、会員登録・ログイン画面、マイページ、CSRF トークン |
| | `VerifiedItems` · `SubmittedForms`（セッションスコープ）、`FormDraft` | パスワード確認を通過した投稿、10分以内の同じ内容の再送信防止、送信できなかった入力の保管 |
| | `BoardQuery` · `LoginMemberAdvice` · `FormErrors` · `SearchKeyword` | 条件を保持した一覧のリンク・ページ番号、全画面共通のログイン情報、エラー文、キーワードの整理 |
| `domain` | `FoundItem` · `LostItem` · `Comment` · `Member` など、`*Form` | データと入力フォーム（Bean Validation）、`Texts`（入力文字列の整理）、`Board`（掲示板の種類） |
| `service` | `JrfindService` · `LostItemService` · `CommentService` · `MemberService` · `MapService` | 業務処理：マスタデータの確認、タイトルの生成、同時変更の処理、ニックネーム変更・退会（トランザクション） |
| | `PasswordAttemptLimiter` | 非会員の投稿のパスワード・ログイン・現在のパスワードの連続失敗ロック（5分） |
| | `ImageStorage` · `ImageResizer` · `OrphanImageCleaner` | 写真の形式確認・縮小・保存・削除、使われない写真の整理 |
| `repository` | `Jdbc*Repository`（インターフェース＋JdbcTemplate による実装） | SQL。条件付き UPDATE・DELETE、`INSERT … SELECT`、一覧の絞り込み・並べ替え・ページ |
| | `KeywordCondition` · `SearchTextBackfill` | 検索条件と検索用の値（NFKC・小文字）、空の検索用カラムを埋める |

## DB 設計（ER 図）

テーブルは7つ（`src/main/resources/schema.sql`）。実線は外部キー、点線は外部キーを使わずアプリケーションで確認する関係です。

```mermaid
erDiagram
  MEMBER |o--o{ FOUND_ITEM : "会員の投稿 (非会員は NULL)"
  MEMBER ||--o{ LOST_ITEM : "投稿"
  MEMBER ||--o{ POST_COMMENT : "投稿"
  FOUND_ITEM |o--o{ POST_COMMENT : "コメント"
  LOST_ITEM |o--o{ POST_COMMENT : "コメント"
  CATEGORY ||..o{ FOUND_ITEM : "CATEGORYNAME"
  CATEGORY ||..o{ LOST_ITEM : "CATEGORYNAME"
  STATION ||..o{ FOUND_ITEM : "LINENAME + STATIONNAME"
  STATION |o..o{ LOST_ITEM : "駅不明なら NULL"
  STATION_LOCATION ||..|{ STATION : "STATIONNAME"

  MEMBER {
    BIGINT ID PK
    VARCHAR LOGINID UK "20, 退会すると NULL"
    VARCHAR PASSWORD "100, BCrypt"
    VARCHAR REALNAME "20"
    VARCHAR NICKNAME "20, NOT NULL"
    DATE BIRTHDATE
    VARCHAR PHONE "20"
    TIMESTAMP AGREEDDATE "NOT NULL, 個人情報の同意"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    TIMESTAMP WITHDRAWNDATE "退会日時"
    VARCHAR LOGINKEY UK "生成カラム LOWER(LOGINID)"
  }
  FOUND_ITEM {
    BIGINT ID PK
    VARCHAR TITLE "200, サーバーで生成"
    DATE FOUNDDATE
    VARCHAR LINENAME "50"
    VARCHAR STATIONNAME "50"
    VARCHAR CATEGORYNAME "50"
    VARCHAR ITEMNAME "100"
    VARCHAR CONTENTS "500"
    VARCHAR IMGPATH "200, 写真がなければ NULL"
    VARCHAR PASSWORD "100, 非会員の投稿のみ BCrypt"
    VARCHAR AUTHOR "20"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    BOOLEAN DEL_FLG "論理削除"
    TIMESTAMP MODIFIEDDATE
    BIGINT MEMBERID FK "非会員の投稿は NULL"
    VARCHAR SEARCHTEXT "検索用, タイトル+本文を NFKC 小文字"
  }
  LOST_ITEM {
    BIGINT ID PK
    VARCHAR TITLE "200"
    DATE LOSTDATE
    VARCHAR LINENAME "50"
    VARCHAR STATIONNAME "50, 駅不明なら NULL"
    VARCHAR CATEGORYNAME "50"
    VARCHAR ITEMNAME "100"
    VARCHAR CONTENTS "500"
    VARCHAR IMGPATH "200"
    VARCHAR AUTHOR "20, 投稿時のニックネーム"
    BIGINT MEMBERID FK "NOT NULL"
    BOOLEAN RESOLVED "見つかりました"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    BOOLEAN DEL_FLG "論理削除"
    TIMESTAMP MODIFIEDDATE
    VARCHAR SEARCHTEXT "検索用"
  }
  POST_COMMENT {
    BIGINT ID PK
    BIGINT FOUNDITEMID FK "どちらか一方のみ"
    BIGINT LOSTITEMID FK "どちらか一方のみ"
    VARCHAR CONTENTS "500"
    VARCHAR AUTHOR "20"
    BIGINT MEMBERID FK "NOT NULL"
    TIMESTAMP REGISTRATIONDATE "NOT NULL"
    BOOLEAN DEL_FLG "論理削除"
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

### 制約

| テーブル | 制約 | 内容 |
|---|---|---|
| `MEMBER` | `CK_MEMBER_WITHDRAWN`（CHECK） | 退会していない会員は ID・パスワード・氏名・生年月日・電話番号がすべてあり、退会した会員（`WITHDRAWNDATE` あり）はすべて NULL |
| `MEMBER` | `LOGINKEY` 生成カラム＋`UK_MEMBER_LOGINKEY` | `LOWER(LOGINID)` にユニークインデックス → `Taro` と `taro` が同時に登録しても DB で防ぐ。退会した会員は NULL なので複数いてよい |
| `MEMBER` | `LOGINID` UNIQUE | 退会すると NULL（同じ ID で再登録できる） |
| `POST_COMMENT` | CHECK `(FOUNDITEMID IS NULL) <> (LOSTITEMID IS NULL)` | コメントは両掲示板のうち、ちょうど1つの投稿だけに紐づく |
| `CATEGORY` | `CATEGORYNAME` UNIQUE | |
| `STATION` | UNIQUE `(LINENAME, STATIONNAME)` | 同じ駅が複数の路線にあれば、路線ごとに1行 |

### インデックス

| 名前 | 対象 | 用途 |
|---|---|---|
| `IX_FOUND_ITEM_REGDATE` | `FOUND_ITEM(REGISTRATIONDATE, ID)` | 作成日順の一覧（既定の並べ替え） |
| `IX_FOUND_ITEM_FOUNDDATE` | `FOUND_ITEM(FOUNDDATE, ID)` | 発見日順の一覧 |
| `IX_LOST_ITEM_REGDATE` | `LOST_ITEM(REGISTRATIONDATE, ID)` | 作成日順の一覧 |
| `IX_LOST_ITEM_LOSTDATE` | `LOST_ITEM(LOSTDATE, ID)` | なくした日順の一覧 |
| `UK_MEMBER_LOGINKEY` | `MEMBER(LOGINKEY)` | 大文字・小文字を区別しない ID の一意性 |

- 外部キーのカラム（`MEMBERID`、`FOUNDITEMID`、`LOSTITEMID`）には H2 がインデックスを自動で作ります（一覧のコメント数、マイページ）。
- マスタデータ（`data.sql`、起動のたびに `MERGE`）：カテゴリ 11、路線・駅 78行（6路線）、駅の位置 47。
- スキーマは起動のたびに `CREATE … IF NOT EXISTS`／`ALTER … IF NOT EXISTS` で反映するため、以前の DB もそのまま新しい構成になります。

## 主なリクエストの流れ

### 1. 非会員の投稿の編集（4桁パスワードの確認 → セッションに記録 → 条件付き UPDATE）

```mermaid
sequenceDiagram
  autonumber
  participant U as ブラウザ
  participant K as CsrfController
  participant C as JrfindController
  participant V as VerifiedItems (セッション)
  participant S as JrfindService
  participant L as PasswordAttemptLimiter
  participant R as JdbcJrfindRepository

  U->>K: GET /csrf (最初の送信時に一度)
  K-->>U: headerName, token (ここでセッションを作成)
  U->>C: POST /items/{id}/check-password (password, X-CSRF-TOKEN)
  C->>S: checkPassword(id, password, id@接続元IP)
  S->>R: findPasswordHash(id)
  alt 存在しない · 削除された · 会員の投稿
    C-->>U: 404 (失敗回数に数えない)
  else ロック中 (5回失敗して5分)
    C-->>U: 429 ok=false, remaining=0
  else 確認
    S->>S: NFKC でそろえ、4桁の数字でなければ数えない
    S->>L: 正しければ reset、間違いなら recordFailure
    C->>V: add(id) (正しい場合)
    C-->>U: 200 ok, remaining, lockMinutes
  end
  U->>C: GET /items/{id}/edit
  C->>V: contains(id)
  C-->>U: 編集画面
  U->>C: POST /items/{id}/edit
  C->>V: contains(id) (切れていれば入力を保管して 302 ?reverify)
  C->>S: updateItem(id, form, file, isImageDeleted)
  S->>R: update(item, 読み込んだときの写真)
  Note over R: UPDATE … WHERE ID=? AND DEL_FLG=FALSE<br/>AND ImgPath IS NOT DISTINCT FROM ?
  alt 0行 (その間に削除された、または写真が変わった)
    S-->>C: InvalidPostException (案内文)
    C-->>U: 編集画面＋案内
  else 1行
    C->>V: remove(id) (再度編集するには再確認)
    C-->>U: 302 /items/{id}
  end
```

### 2. 会員ログイン（連続失敗の制限）

```mermaid
sequenceDiagram
  autonumber
  participant U as ブラウザ
  participant G as LoginAttemptGuard (フィルター)
  participant A as Spring Security 認証
  participant M as MemberService
  participant L as PasswordAttemptLimiter

  U->>G: POST /login (loginId, password, next, _csrf)
  G->>M: isLoginLocked(loginId, 接続元IP)
  M->>L: ID+IP で5回、IP で20回が基準
  alt ロック中
    G-->>U: 302 /login?locked (パスワードを確認しない)
  else ロックなし
    G->>A: 次のフィルターへ
    A->>M: loadUserByUsername (ID の前後の空白を整理) + BCrypt で比較
    alt 成功
      A->>G: onAuthenticationSuccess
      G->>M: recordLoginSuccess (その ID+IP の回数を削除、IP の回数は残す)
      G-->>U: 302 next または保存した画面または /
    else ID · パスワードの誤り
      A->>G: onAuthenticationFailure
      G->>M: recordLoginFailure (存在しない ID も同じように数える)
      G-->>U: 302 /login?error または ?locked (入力した ID、残り回数は flash)
    end
  end
```

### 3. 写真を差し替える編集（形式の確認 → 縮小・EXIF 削除 → 保存 → 条件付き UPDATE → 以前のファイルを削除）

```mermaid
sequenceDiagram
  autonumber
  participant C as Controller
  participant S as Service
  participant I as ImageStorage
  participant Z as ImageResizer
  participant FS as アップロードフォルダ
  participant R as Repository

  C->>S: updateItem(id, form, imageFile)
  S->>S: 路線 · 駅 · カテゴリの実在、日付の範囲を確認
  S->>I: store(imageFile)
  I->>I: ファイル先頭 (マジックナンバー) で JPG · PNG · GIF · WEBP を判別
  opt 画像ではない
    I-->>C: InvalidPostException
  end
  I->>Z: process(bytes, 拡張子)
  Z->>Z: サイズだけを先に読み、5,000万画素を超えれば拒否
  Z->>Z: スマホの回転情報を反映し、長辺 1,280px に縮小
  Z->>Z: JPG はメタデータなしで保存し直す (EXIF · GPS を削除)
  I->>FS: yyyyMMddHHmmss_8桁.jpg で保存
  S->>R: update(item, 読み込んだときの写真)
  alt 0行 (同時に削除 · 変更された)
    S->>I: delete(新しいファイル)
    S-->>C: InvalidPostException (案内)
  else 1行
    S->>I: delete(以前のファイル) (DB に反映した後に削除)
  end
  Note over FS: それでも残ったファイルは OrphanImageCleaner が<br/>毎日 04:00、1時間以上たったものだけ整理
```

## 設計上の判断

| テーマ | 選択 | 理由 |
|---|---|---|
| DB アクセス | JdbcTemplate（JPA は使わない） | 実習で身につけた方法で、テーブルも7つと小さい構成です。条件付き UPDATE、`INSERT … SELECT`、LIKE のエスケープのように、実行される SQL をそのまま見て制御したい箇所が多くあります。 |
| 同時の編集・削除 | 条件付き UPDATE・DELETE | `WHERE DEL_FLG = FALSE AND ImgPath IS NOT DISTINCT FROM ?` で「読み込んだときのままの場合だけ」反映します。遅れた側には案内し、新しく保存した写真は削除するため、削除した投稿に写真が公開されたまま残ったり、使われないファイルがたまったりしません。削除は写真が変わっていれば読み直し、最大5回試します。 |
| コメントの保存 | `INSERT … SELECT … WHERE DEL_FLG = FALSE` | 投稿の存在確認と保存を1文で行うため、確認の直後に投稿が削除されてもコメントは入りません。 |
| 検索 | 検索用カラム `SEARCHTEXT` | 保存時にタイトル＋本文を NFKC・小文字にそろえておき、キーワードも同じ方法でそろえて1つのカラムだけを比較します。以前は検索のたびに全投稿を `LOWER(TRANSLATE(…))` で変換していたため、投稿1万件で最も遅い検索が約2.9秒かかっていましたが、今は数十 ms です（[README パフォーマンス](../README.ja.md#パフォーマンス)）。 |
| 一覧の並べ替え | `(作成日・発見日・なくした日, ID)` のインデックス | インデックスの順に先頭から1ページ分だけを読みます。インデックスがないと、全投稿のコメント数を数えてから並べ替える必要がありました。同じ日付の投稿がページごとに入れ替わらないよう、ID も一緒に使います。 |
| マスタデータ | `@Cacheable`（メモリ） | 路線・駅・カテゴリ・駅の位置は起動時に `data.sql` でしか変わらないため、最初に一度だけ読みます。変更できない List で返します。 |
| セッション | ログインせずに見るだけなら作らない | CSRF トークンを作るとセッションが作られるため、閲覧画面には入れず、JS が送信するときに `GET /csrf` で受け取ります。訪問者・検索ロボットごとにサーバーのメモリにセッションがたまりません。 |
| 非会員の投稿の権限 | セッションスコープの `VerifiedItems` | パスワード確認の結果を画面（JS）ではなくサーバーのセッションに記録し、編集・削除のリクエストごとにサーバーが確認します。編集が終わると消して、再度確認させます。 |
| 写真 | 形式の確認＋縮小＋EXIF 削除 | 拡張子や Content-Type は書き換えられるため、ファイル先頭で判別します。スマホ写真の撮影場所（GPS）が公開されないよう、JPG は必ず保存し直します。 |
| 画面のスクリプト | すべてファイル、CSP `script-src 'self'` | インラインスクリプト・`on…` 属性をなくし、投稿にスクリプトが紛れ込んでも実行されないようにしました。外部ライブラリは WebJars で同梱しています。 |
