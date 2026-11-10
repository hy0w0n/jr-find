# API 仕様（HTTP エンドポイント）

[한국어](API.md) | **日本語** · [README](../README.ja.md) · [構成・DB 設計](ARCHITECTURE.ja.md)

JR Find は、サーバーで画面（Thymeleaf）を生成して返す Web アプリケーションです。ほとんどのエンドポイントは HTML 画面かリダイレクト（登録・編集後の PRG）を返し、
画面の JS が使う JSON エンドポイントは3つです（`/items/{id}/check-password`、`/map/stations`、`/csrf`）。
画面の文言とエラーメッセージは日本語です。以下のパスは `http://localhost:8080` を基準とします。

## 目次

- [一覧](#一覧)
- [共通ルール](#共通ルール)
- [1. ホーム・統合検索](#1-ホーム統合検索)
- [2. 見つけた 掲示板](#2-見つけた-掲示板)
- [3. 探しています 掲示板](#3-探しています-掲示板)
- [4. コメント](#4-コメント)
- [5. 会員登録・ログイン](#5-会員登録ログイン)
- [6. マイページ](#6-マイページ)
- [7. 駅マップ](#7-駅マップ)
- [8. ユーティリティ・静的ファイル](#8-ユーティリティ静的ファイル)
- [JSON エンドポイントの例](#json-エンドポイントの例)
- [セキュリティヘッダー](#セキュリティヘッダー)

## 一覧

エンドポイントは **40個**（コントローラー 38 ＋ Spring Security が処理する `POST /login`、`POST /logout`）。静的ファイルのパスは別にまとめています。

権限の表記：**誰でも** ／ **会員**（ログインが必要）／ **投稿者**（その投稿・コメントを書いた会員）／ **確認済みセッション**（非会員の投稿の4桁パスワード確認を通過したセッション）

| # | メソッド | パス | 権限 | 説明 | 応答 |
|---:|---|---|---|---|---|
| 1 | GET | `/` | 誰でも | ホーム（統合検索ボックス、両掲示板への入口） | 画面 |
| 2 | GET | `/search` | 誰でも | 統合検索（両掲示板の最新5件ずつ＋件数） | 画面 |
| 3 | GET | `/items` | 誰でも | 見つけた 一覧（絞り込み・検索・並べ替え・ページ） | 画面 |
| 4 | GET | `/items/new` | 誰でも | 見つけた 登録画面 | 画面 |
| 5 | POST | `/items/new` | 誰でも | 見つけた 登録（非会員は4桁パスワード） | 302 |
| 6 | GET | `/items/{id}` | 誰でも | 見つけた 詳細 | 画面 |
| 7 | POST | `/items/{id}/check-password` | 誰でも | 非会員の投稿のパスワード確認（JS） | JSON |
| 8 | POST | `/items/{id}/verify` | 誰でも | 非会員の投稿のパスワード確認（JS なし） | 302 |
| 9 | GET | `/items/{id}/edit` | 投稿者／確認済みセッション | 見つけた 編集画面 | 画面／302 |
| 10 | POST | `/items/{id}/edit` | 投稿者／確認済みセッション | 見つけた 編集 | 302 |
| 11 | POST | `/items/{id}/delete` | 投稿者／確認済みセッション | 見つけた 削除（論理削除） | 302 |
| 12 | GET | `/lost-items` | 誰でも | 探しています 一覧 | 画面 |
| 13 | GET | `/lost-items/{id}` | 誰でも | 探しています 詳細 | 画面 |
| 14 | GET | `/lost-items/new` | 会員 | 探しています 登録画面（投稿前の注意事項） | 画面 |
| 15 | POST | `/lost-items/new` | 会員 | 探しています 登録 | 302 |
| 16 | GET | `/lost-items/{id}/edit` | 投稿者 | 探しています 編集画面 | 画面／302 |
| 17 | POST | `/lost-items/{id}/edit` | 投稿者 | 探しています 編集 | 302 |
| 18 | POST | `/lost-items/{id}/resolve` | 投稿者 | 「見つかりました」の表示・解除 | 302 |
| 19 | POST | `/lost-items/{id}/delete` | 投稿者 | 探しています 削除（論理削除） | 302 |
| 20 | GET | `/items/{id}/comments` | 会員 | ログイン後、見つけた 詳細のコメント位置へ | 302 |
| 21 | POST | `/items/{id}/comments` | 会員 | 見つけた コメント登録 | 302 |
| 22 | POST | `/items/{id}/comments/{commentId}/delete` | 投稿者 | 見つけた コメント削除 | 302 |
| 23 | GET | `/lost-items/{id}/comments` | 会員 | ログイン後、探しています 詳細のコメント位置へ | 302 |
| 24 | POST | `/lost-items/{id}/comments` | 会員 | 探しています コメント登録 | 302 |
| 25 | POST | `/lost-items/{id}/comments/{commentId}/delete` | 投稿者 | 探しています コメント削除 | 302 |
| 26 | GET | `/login` | 誰でも | ログイン画面 | 画面 |
| 27 | POST | `/login` | 誰でも | ログイン（Spring Security、連続失敗の制限） | 302 |
| 28 | POST | `/logout` | 誰でも | ログアウト（Spring Security） | 302 |
| 29 | GET | `/signup` | 誰でも | 会員登録画面 | 画面 |
| 30 | POST | `/signup` | 誰でも | 会員登録 | 302／画面 |
| 31 | GET | `/mypage` | 会員 | マイページ（自分の投稿・コメントのタブ） | 画面 |
| 32 | GET | `/mypage/edit` | 会員 | 会員情報変更画面 | 画面 |
| 33 | POST | `/mypage/edit` | 会員 | 会員情報変更 | 302／画面 |
| 34 | GET | `/mypage/password` | 会員 | パスワード変更画面 | 画面 |
| 35 | POST | `/mypage/password` | 会員 | パスワード変更 | 302／画面 |
| 36 | GET | `/mypage/withdraw` | 会員 | 退会画面 | 画面 |
| 37 | POST | `/mypage/withdraw` | 会員 | 退会 | 302／画面 |
| 38 | GET | `/map` | 誰でも | 駅マップ画面 | 画面 |
| 39 | GET | `/map/stations` | 誰でも | 駅の位置・路線・駅ごとの投稿数 | JSON |
| 40 | GET | `/csrf` | 誰でも | CSRF トークン（JS が送信直前に取得） | JSON |

## 共通ルール

### 権限

- ログインが必要なパス（`SecurityConfig`）：`/lost-items/new`、`/lost-items/*/edit`、`/lost-items/*/delete`、`/lost-items/*/resolve`、
  `/items/*/comments/**`、`/lost-items/*/comments/**`、`/mypage/**`。
  ログインせずにアクセスすると `302 /login?required` に移動し、ログイン後に元の画面へ戻ります。
- それ以外のパスは誰でも開けます。投稿・コメントの編集・削除の権限は各コントローラーで確認します。
  - 会員の投稿：投稿者本人のみ。非会員の投稿（見つけた）：4桁パスワードの確認を通過したセッションのみ（`VerifiedItems`、セッションスコープ）
  - 権限なしで編集・削除を送ると `403`。存在しない投稿・削除された投稿は `404`（エラー画面）

### CSRF

- すべての POST に CSRF トークンが必要です。フォームは hidden 項目 `_csrf`（`th:action` が自動で追加）、JS の `fetch` はヘッダー `X-CSRF-TOKEN`。
- トークンを作るとセッションが作られるため、ログインしていない訪問者が投稿を見るだけの画面（見つけた 詳細など）にはトークンを入れません。
  JS は送信直前に `GET /csrf` でトークンを受け取り、受け取っておいたトークンがセッション切れで無効（`403`）なら、新しく受け取って一度だけ送り直します。
  JS なしでサーバーがパスワード入力ダイアログ・削除確認ダイアログを開いた画面（`?verify=`、`?confirmDelete`）と投稿者本人の画面にだけ、フォームにトークンを入れます。

### 長時間開いたフォーム（`ExpiredFormHandler`）

- 画面のフォーム（`_csrf` パラメーターがあるリクエスト）を送ったときにトークンが一致しなければ、`403` ではなく元の画面（同じサイトの Referer）に `?expired` を付けて戻し、案内します。
- 投稿の登録・編集（`/items/new`、`/items/*/edit`、`/lost-items/new`、`/lost-items/*/edit`）とコメント登録は、入力内容を保管し（`FormDraft`：1セッションに1件、2,000文字まで）
  同じフォームを再度開いたときに戻します。ログインが切れていれば再ログイン後、非会員の投稿はパスワード再確認後に戻します。写真は保管しないため、選び直すよう案内します。
- `POST /logout` ならホームへ。画面のフォームではないリクエスト（fetch）はそのまま `403`。

### 同じ内容の再送信（`SubmittedForms`）

- 同じセッションが同じ場所に同じ内容（写真はファイル名・サイズ）を **10分** 以内に再送信した場合は1回だけ保存し、最初に送ったときと同じ画面へ移動します。
  対象：`POST /items/new`、`POST /lost-items/new`、コメント登録。（登録後に「戻る」で戻って再送信した場合や、ボタンの連打）

### 試行回数の制限（`PasswordAttemptLimiter`）

| 対象 | 数える単位 | 上限 | ロック | ロック中 |
|---|---|---|---|---|
| 非会員の投稿の4桁パスワード | 投稿＋接続元 IP | 5回 | 5分 | `check-password` は `429`、`verify` は案内文 |
| ログイン | ID＋接続元 IP／接続元 IP | 5回／20回 | 5分 | `302 /login?locked`（パスワードを確認しない） |
| マイページの現在のパスワード（パスワード変更・退会） | 会員 | 5回 | 5分 | 入力欄のエラー文 |

- 最後に間違えてから5分たつと、最初から数え直します。残りが3回以下になると画面で案内します。
- 非会員の投稿のパスワードは NFKC でそろえたうえで（全角数字 → 半角）、4桁の数字でなければ失敗回数に数えません。存在しない投稿・会員の投稿は `404` で、数えません。
- 接続元 IP は `request.getRemoteAddr()` のみを使います（`X-Forwarded-For` はリクエストする側が書き換えられるため）。

### 入力値

- サーバーでも再検証します（Bean Validation ＋ 路線・駅・カテゴリがマスタデータに存在するか）。形式エラーのときは同じ画面を再表示し、入力値は保持します。
- 検証の前に整える：改行は LF に、前後の空白（全角・NBSP）とゼロ幅文字は削除、文字の向きを変える制御文字（U+202A–202E、U+2066–2069）はどこにあっても削除。
- 写真：ファイル先頭（マジックナンバー）で JPG・PNG・GIF・WEBP のみ許可、ファイル・リクエストのサイズは 10MB まで、5,000万画素を超えると拒否。
  長辺が 1,280px を超える JPG・PNG は縮小し、JPG は保存し直して撮影情報（EXIF・GPS）を削除します。保存するファイル名はサーバーで生成します（`yyyyMMddHHmmss_8桁.拡張子`）。

## 1. ホーム・統合検索

### `GET /`
- 応答：画面 `home`

### `GET /search`
| パラメーター | 説明 |
|---|---|
| `q` | キーワード。前後の空白を削除し、空なら入力の案内だけを表示。空白（全角を含む）で区切った単語を **すべて** 含む投稿（最大5単語、超えると `keywordTruncated` で案内） |

- 応答：画面 `search` — 両掲示板の最新5件（`foundItems`、`lostItems`）と総件数（`foundCount`、`lostCount`）。すべての結果は各掲示板の一覧 `?q=` へ。
- 比較方法：タイトル＋本文を NFKC・小文字にそろえた `SEARCHTEXT` カラムに対する `LIKE`。大文字・小文字、全角・半角を区別しない（ｽﾏﾎ = スマホ、Ｓｕｉｃａ = suica）。`%` `_` は文字そのものとして検索。

## 2. 見つけた 掲示板

### `GET /items`
| パラメーター | 説明 |
|---|---|
| `line`、`station`、`category` | 路線・駅・カテゴリの絞り込み（値があるものだけ） |
| `q` | キーワード（`/search` と同じルール） |
| `sort` | `latest`（既定：作成日の新しい順）、`reg_asc`、`date_desc`（発見日）、`date_asc`。それ以外の値は `latest`。同じ日付は ID で並べ直す |
| `page` | 1から。0・負の数・最終ページより大きい値は範囲内に補正。1ページ15件 |

- 応答：画面 `ItemList`（一覧、総件数、ページ番号、条件を保持した並べ替え・ページのリンク）

### `GET /items/new`
- 応答：画面 `registerForm`。時間がたって送信できなかった入力があれば戻して表示

### `POST /items/new` — `multipart/form-data`
| パラメーター | 必須 | ルール |
|---|---|---|
| `foundDate` | ○ | `yyyy-MM-dd`、2000-01-01 〜 今日 |
| `lineName`、`stationName` | ○ | マスタデータに存在する路線・駅の組み合わせ |
| `categoryName` | ○ | マスタデータのカテゴリ |
| `itemName` | ○ | 100文字以内 |
| `contents` | ○ | 500文字以内 |
| `imageFile` | | 写真（上記の入力値ルール） |
| `password` | 非会員のみ | 半角数字4桁（BCrypt で保存） |

- タイトル（`[日付][路線][駅][カテゴリ] 物`）と投稿者名（会員はニックネーム、非会員は `匿名の天使 001` 形式の番号）はサーバーで生成します。
- 成功：`302 /items` ・入力エラー：画面 `registerForm`（エラー文、入力値を保持）・同じ内容の再送信：`302 /items`（保存しない）

### `GET /items/{id}`
| パラメーター | 説明 |
|---|---|
| `verify=edit\|delete` | JS なしで「編集」「削除」を押した場合：パスワード入力ダイアログを開いた画面（非会員の投稿） |
| `reverify` | 編集を送ったときにパスワード確認が切れていた場合：再確認のダイアログ（入力内容は保管済み） |
| `confirmDelete` | 削除確認ダイアログを開いた画面（権限がある場合のみ） |
| `allComments` | すべてのコメントを表示（なければ最新50件＋「以前のコメント(N件)をすべて表示」） |

- 応答：画面 `itemDetail` ・存在しない・削除された投稿：`404`
- ログインせずに見るだけならセッションを作りません（CSRF トークンを入れない）。

### `POST /items/{id}/check-password` — JSON
- リクエスト：フォーム `password`、ヘッダー `X-CSRF-TOKEN`
- `200` `{"ok": true|false, "remaining": 残り回数, "lockMinutes": 5}` — 正しければこのセッションを「確認済み」として記録
- `429` `{"ok": false, "remaining": 0, "lockMinutes": 5}` — 連続で間違えてロック
- `404`（本文なし）— 存在しない投稿・削除された投稿・会員の投稿（失敗回数に数えない）
- `403` — CSRF トークンがない・一致しない（JS はトークンを受け取り直して一度だけ送り直す）

### `POST /items/{id}/verify`
JS を使わないパスワード確認（結果は画面遷移で返す）。

| パラメーター | 説明 |
|---|---|
| `action` | `edit`（既定）／`delete` |
| `password` | 4桁のパスワード |

- 正しい：`302 /items/{id}/edit` または `302 /items/{id}?confirmDelete`
- 間違い・ロック：`302 /items/{id}?verify=edit|delete` ＋案内文（flash `verifyError`、残り回数を含む）・存在しない投稿：`404`

### `GET /items/{id}/edit`
- 権限あり：画面 `editForm`（送信できなかった編集内容があれば戻す）
- 権限なし：
  - 会員の投稿でログインしていない：`302 /login?required&next=/items/{id}/edit`
  - 非会員の投稿で保管した編集内容がある：`302 /items/{id}?reverify`
  - それ以外：`302 /items/{id}`

### `POST /items/{id}/edit` — `multipart/form-data`
- パラメーター：`POST /items/new` と同じ（`password` を除く）＋ `isImageDeleted`（`true` なら写真を削除）
- 非会員の投稿で確認が切れていれば入力を保管して `302 /items/{id}?reverify`（確認後、編集画面に戻す）。会員の投稿を本人以外が送ると `403`
- 成功：`302 /items/{id}`（非会員の投稿は確認状態を消す → 再度編集するには再確認）。何も変えていなければ保存しない（「編集済み」を付けない）
- 同時に別のリクエストが投稿を削除した・写真を変えた場合は反映せずに案内（条件付き UPDATE、新しく保存した写真ファイルは削除）

### `POST /items/{id}/delete`
- 権限：投稿者／確認済みセッション（それ以外は `403`）
- 論理削除（`DEL_FLG`）し、写真ファイルも削除 → `302 /items`

## 3. 探しています 掲示板

### `GET /lost-items`
- パラメーター：`GET /items` と同じ。駅の絞り込み `station=unknown` は「駅不明」（駅がわからない投稿）
- 応答：画面 `lostList`

### `GET /lost-items/{id}`
- パラメーター：`confirmDelete`（投稿者のみ）、`allComments`
- 応答：画面 `lostDetail` ・存在しない・削除された投稿：`404`

### `GET /lost-items/new`（会員）
- 応答：画面 `lostForm`。最初に投稿前の注意事項（詐欺・窃盗の警告）のダイアログを表示

### `POST /lost-items/new`（会員）— `multipart/form-data`
| パラメーター | 必須 | ルール |
|---|---|---|
| `lostDate` | ○ | `yyyy-MM-dd`、2000-01-01 〜 今日 |
| `lineName` | ○ | マスタデータの路線 |
| `stationName` | ○ | その路線の駅、または `unknown`（駅がわからない → DB には NULL） |
| `categoryName`、`itemName`、`contents`、`imageFile` | | 見つけた と同じ |
| `warningConfirmed` | ○ | `true` — 投稿前の注意事項を確認したか（サーバーでも確認） |

- 成功：`302 /lost-items` ・入力エラー・注意事項の未確認：画面 `lostForm`

### `GET /lost-items/{id}/edit`（会員）
- 投稿者：画面 `lostForm` ・投稿者以外：`302 /lost-items/{id}`

### `POST /lost-items/{id}/edit`（投稿者）
- パラメーター：登録と同じ＋ `isImageDeleted` ・投稿者以外：`403` ・成功：`302 /lost-items/{id}`

### `POST /lost-items/{id}/resolve`（投稿者）
- パラメーター：`resolved`（既定 `true`、`false` なら解除）→ `302 /lost-items/{id}`

### `POST /lost-items/{id}/delete`（投稿者）
- 論理削除＋写真ファイルの削除 → `302 /lost-items`

## 4. コメント

両掲示板で共通。`{board}` は `items`（見つけた）または `lost-items`（探しています）。

### `GET /{board}/{id}/comments`（会員）
- ログインしていない人がコメント欄の「ログイン」を押すと来るアドレス。ログイン後 `302 /{board}/{id}#comments`
  （ログインが切れて送れなかったコメントがあれば `#commentForm` に移動し、入力欄に戻す）

### `POST /{board}/{id}/comments`（会員）
| パラメーター | ルール |
|---|---|
| `contents` | 必須、500文字以内 |

- 成功：`302 /{board}/{id}#comment-{commentId}`（登録したコメントの位置）
- 入力エラー：`302 /{board}/{id}#commentForm` ＋ flash `commentError`、`commentDraft`（入力内容を保持）
- 存在しない・削除された投稿：`404`（確認と保存を `INSERT … SELECT` の1文で行うため、確認の直後に投稿が削除されてもコメントは入らない）
- 同じ内容の再送信：`302 /{board}/{id}#comments`（保存しない）

### `POST /{board}/{id}/comments/{commentId}/delete`（投稿者）
- コメントを書いた本人のみ（それ以外は `403`）、その投稿のコメントでなければ `404` → `302 /{board}/{id}#comments`

## 5. 会員登録・ログイン

### `GET /login`
| パラメーター | 画面の案内 |
|---|---|
| `required` | ログインが必要な画面から来た |
| `error`／`locked` | ID・パスワードの誤り／連続失敗でロック |
| `registered` | 登録完了 |
| `reauth` | 別の場所でパスワードが変更され、ログインが終了した |
| `next` | ログイン後に戻る画面（ヘッダーの「ログイン」が今の画面のアドレスを付ける） |

- すでにログインしている：`302 /`

### `POST /login`（Spring Security）
| パラメーター | 説明 |
|---|---|
| `loginId`、`password` | ID・パスワード |
| `next` | 戻る画面。このサイト内のパスのみ使用（`//`、`\`、制御文字、`/login`・`/logout` は無視） |

- 成功：`next` → ログインが必要で保存しておいた画面 → `/` の順に `302`
- 失敗：`302 /login?error[&next=…]`（入力した ID・残り回数は flash）・ロック：`302 /login?locked`（ロック中は正しいパスワードも確認しない）

### `POST /logout`（Spring Security）
- `302 /?logout`。セッション Cookie `JSESSIONID` を削除し、`Clear-Site-Data: "cache"`（ログアウト後に「戻る」で前の画面が見えないように）

### `GET /signup`
- 応答：画面 `signup` ・ログイン中：`302 /`

### `POST /signup`
| パラメーター | ルール |
|---|---|
| `loginId` | 半角英数字・`_` で4〜20文字。大文字・小文字だけが違う ID も重複とみなす（DB の `LOGINKEY` ユニークインデックス） |
| `password`／`passwordConfirm` | 英字と数字を含む8〜64文字（半角）／同じ値 |
| `realName` | 必須、20文字以内 |
| `nickname` | 20文字以内、空なら `realName`。`匿名の天使` で始まる名前と `退会したユーザー` は使用不可 |
| `birthDate` | 過去の日付、1900-01-01 以降 |
| `phone` | 日本の電話番号の形式（ハイフンの有無は問わない、10〜11桁） |
| `agree` | `true`（個人情報の取り扱いへの同意） |

- 成功：`302 /login?registered` ・エラー：画面 `signup`（入力値・エラー文）

## 6. マイページ

すべて会員専用。

### `GET /mypage`
| パラメーター | 説明 |
|---|---|
| `tab` | `found`／`lost`／`comments`。なければ投稿・コメントがある最初のタブ |
| `page` | 1ページ10件 |

- 応答：画面 `mypage`（会員情報、タブごとの件数、一覧）。コメントのリンクはすべてのコメントを表示するアドレス（`?allComments#comment-{id}`）

### `GET /mypage/edit` ・ `POST /mypage/edit`
- パラメーター：`realName`、`nickname`、`birthDate`、`phone`（会員登録と同じルール）
- 成功：`302 /mypage?updated`。ニックネームを変えると、これまでの投稿・コメントの投稿者名も1トランザクションで変更し、再ログインなしでヘッダーに反映

### `GET /mypage/password` ・ `POST /mypage/password`
- パラメーター：`currentPassword`、`newPassword`、`newPasswordConfirm`（現在と同じパスワードは不可）
- 成功：`302 /mypage?passwordChanged`。他のブラウザ・端末のログインは終了（そちらは次のリクエストで `/login?reauth`）

### `GET /mypage/withdraw` ・ `POST /mypage/withdraw`
- パラメーター：`currentPassword`、`agree=true`
- 成功：`302 /?withdrawn`。個人情報（ID・パスワード・氏名・生年月日・電話番号）を削除し、投稿・コメントは「退会したユーザー」として残す。すべてのセッションを終了

## 7. 駅マップ

### `GET /map`
- 応答：画面 `map`（Leaflet の地図＋「駅を選ぶ」の一覧。地図が使えなくても駅を選んで掲示板へ移動できる）

### `GET /map/stations` — JSON
- 路線ごとの駅の順序（路線図の描画に使用）と、駅ごとの位置・両掲示板の投稿数。下の[例](#get-mapstations)を参照

## 8. ユーティリティ・静的ファイル

### `GET /csrf` — JSON
- `{"headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token": "…"}`。このリクエストでセッションが作られます。

### 静的ファイル（エンドポイント数には含めない）

| パス | 内容 | キャッシュ |
|---|---|---|
| `/css/**`、`/js/**` | 画面共通のスタイル・スクリプト。画面にはファイル内容のハッシュを付けたアドレス（`mobile-<ハッシュ>.css`）で出力 | 1年（`public`） |
| `/webjars/**` | flatpickr 4.6.13、Leaflet 1.9.4（プロジェクトに同梱、外部 CDN は使わない） | 1年 |
| `/logo/**` | ロゴ（ハッシュを付けたアドレス） | 1年 |
| `/images/**` | アップロードした写真（`jrfind.upload-dir`、既定 `./uploads`）。ファイル名は保存のたびに新しく生成 | 1年 |
| `/h2-console/**` | H2 コンソール。環境変数 `JRFIND_H2_CONSOLE=true` のときのみ、localhost からのみ | — |

- レスポンス圧縮（gzip）：HTML・CSS・JS・JSON、1KB 以上

## JSON エンドポイントの例

### `POST /items/{id}/check-password`

```js
// 1) トークンを受け取る（最初の送信時に一度）
const csrf = await (await fetch('/csrf', { headers: { Accept: 'application/json' } })).json();
// 2) パスワードを確認
const res = await fetch('/items/42/check-password', {
  method: 'POST',
  headers: { [csrf.headerName]: csrf.token },
  body: new URLSearchParams({ password: '1234' })
});
```

| 状況 | ステータス | 本文 |
|---|---|---|
| 正しい | `200` | `{"ok":true,"remaining":5,"lockMinutes":5}` |
| 間違い（2回目） | `200` | `{"ok":false,"remaining":3,"lockMinutes":5}` |
| ロック | `429` | `{"ok":false,"remaining":0,"lockMinutes":5}` |
| 存在しない・削除された・会員の投稿 | `404` | （なし） |
| CSRF トークンがない・期限切れ | `403` | （なし） |

### `GET /csrf`

```json
{
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "kT0x…(リクエストごとに異なる値)"
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

- `lostCenter`：JR東日本のお忘れ物承り所がある駅（東京、大宮）
- データ：6路線、47駅（`STATION`、`STATION_LOCATION`）。路線・駅・カテゴリ・駅の位置は実行中に変わらないため、最初に一度読んだものをメモリに保持します。

## セキュリティヘッダー

すべての画面の応答に付きます（`SecurityConfig`）。

| ヘッダー | 値 |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tile.openstreetmap.org; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Cache-Control`（画面） | `no-cache, no-store, max-age=0, must-revalidate`（Spring Security の既定値） |

- スクリプトはすべてファイルで読み込むため、画面にインラインスクリプト・`on…` 属性はありません（テストで全画面を検査）。投稿などにスクリプトが紛れ込んでも実行されません。
- セッション Cookie：`HttpOnly`、`SameSite=Lax`、URL にセッション ID を付けない（`tracking-modes=cookie`）
