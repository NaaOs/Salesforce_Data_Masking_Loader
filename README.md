# Salesforce Data Masking Loader

DataloaderベースのSalesforceデータマスキング投入ツールです。
YAMLファイルで定義されたマスキングルールに基づいて、CSVデータをマスキングしてSalesforceに投入します。

**JavaFX GUIにより、直感的な操作が可能です。**

## 主な機能

- **GUIによる簡単操作** - ファイル選択、設定、実行をすべてGUI上で実行
- CSVファイルからデータを読み込み、指定項目をマスキング
- マスキングルールはYAMLファイルで柔軟に設定可能
- マスキング済みデータのCSV出力
- Salesforceへの直接Insert/Upsert
- ストリーミング＋バッチ処理による大量データ対応（全件をメモリに載せない）
- 投入に失敗したレコードをエラーCSVに出力し、件数を画面・終了コードで通知
- リアルタイムログ表示とプログレスバー

## 画面構成

### メイン画面
- **設定ファイルセクション**: マスキング設定とSalesforce設定ファイルの選択
- **データ投入設定セクション**: 入力CSVファイル、オブジェクト、処理モードの選択
- **実行ボタン**: 処理の開始
- **プログレスバー**: 処理の進捗状況をリアルタイム表示
- **ログエリア**: 実行ログをリアルタイムで表示

## マスキングタイプ

以下のマスキングタイプをサポート:

- `RANDOM_STRING`: ランダム英数字（`length` は 1〜255、既定 20）
- `PHONE`: 電話番号形式（090-XXXX-XXXX）
- `EMAIL`: ランダムなメールアドレス
- `FIRST_NAME`: ランダムな名前（名）
- `LAST_NAME`: ランダムな名前（姓）
- `ADDRESS`: ランダムな住所
- `COMPANY_NAME`: ランダムな会社名
- `FIXED_VALUE`: 固定値（`value` の指定が必須）
- `NUMBER`: ランダムな数値（`length` は最大桁数、1〜18、既定 10）
- `DATE`: ランダムな日付

## 必要な環境

- Java 17以上（JavaFX 21 の要件）
- Maven 3.6以上

## セットアップ

### 1. プロジェクトのビルド

```bash
mvn clean package
```

実行可能JARファイルが `target/data-masking-loader-1.0.0-jar-with-dependencies.jar` に生成されます。

### 2. 設定ファイルの準備

#### masking-config.yml

マスキング対象のオブジェクトと項目を定義します。

```yaml
objects:
  - name: Account
    fields:
      - name: Name
        maskingType: RANDOM_STRING
        length: 20
      - name: Phone
        maskingType: PHONE
      - name: BillingStreet
        maskingType: ADDRESS
```

#### salesforce.properties

Salesforce接続情報を設定します。

```properties
sf.username=your-username@example.com
sf.password=YourPassword
sf.securityToken=YourSecurityToken
sf.loginUrl=https://login.salesforce.com/services/Soap/u/58.0
sf.batchSize=200
```

`sf.batchSize` は 1〜200 で指定します（SOAP API の 1 回あたり上限）。

**認証情報を環境変数で渡す（推奨）**: パスワードをファイルに平文で保存しないよう、以下の環境変数を設定するとプロパティファイルの値より優先されます。`SF_USERNAME` と `SF_PASSWORD` を設定すれば、プロパティファイルがなくても実行できます。

| 環境変数 | 対応するプロパティ |
|---|---|
| `SF_USERNAME` | `sf.username` |
| `SF_PASSWORD` | `sf.password` |
| `SF_SECURITY_TOKEN` | `sf.securityToken` |

`salesforce.properties` は `.gitignore` に登録済みです。リポジトリにはひな形の `salesforce_sample.properties` のみを置いてください。

**注意**: Sandbox環境の場合は `sf.loginUrl` を以下に変更してください:
```
sf.loginUrl=https://test.salesforce.com/services/Soap/u/58.0
```

## 使い方

### GUI版の起動

```bash
# Mavenでビルド
mvn clean package

# GUIアプリケーションを起動
mvn javafx:run
```

または、ビルド後のJARファイルを**引数なし**で直接実行:

```bash
java -jar target/data-masking-loader-1.0.0-jar-with-dependencies.jar
```

（起動時に `WARNING: Unsupported JavaFX configuration` と表示されますが、動作に問題はありません）

### GUI操作手順

1. **マスキング設定ファイルの読み込み**
   - 「マスキング設定」欄で `masking-config.yml` を選択
   - 「読み込み」ボタンをクリック
   - オブジェクト一覧が自動的に読み込まれます

2. **Salesforce設定ファイルの選択**
   - 「Salesforce設定」欄で `salesforce.properties` を選択

3. **入力CSVファイルの選択**
   - 「入力CSVファイル」欄で処理対象のCSVファイルを選択

4. **処理設定**
   - オブジェクト名: ドロップダウンから選択
   - 処理モード: 以下から選択
     - `mask-only (マスキングのみ)`: CSVファイルにマスキングして出力
     - `mask-insert (Insert)`: マスキングしてSalesforceにInsert
     - `mask-upsert (Upsert)`: マスキングしてSalesforceにUpsert
   - 外部ID項目: Upsertモードの場合のみ指定

5. **実行**
   - 「実行」ボタンをクリック
   - プログレスバーで進捗（処理件数・成功／失敗件数）を確認
   - ログエリアで詳細を確認
   - 投入に失敗したレコードがある場合は警告ダイアログが表示され、エラーCSVが出力されます

### コマンドライン版

JARに**引数を付けて**実行するとコマンドライン版として動作します（引数なしの場合はGUIが起動します）。
`masking-config.yml` と `salesforce.properties` はカレントディレクトリから読み込みます。

```bash
java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar <処理モード> <オブジェクト名> <CSVファイルパス> [外部ID項目]
```

### 終了コード

| コード | 意味 |
|---|---|
| 0 | 正常終了（処理対象 0 件を含む） |
| 1 | エラー（引数・設定・ファイル不備、Salesforce 接続／通信エラー） |
| 2 | 一部レコードの投入に失敗（エラーCSVを出力） |

### 処理モード

#### 1. mask-only
CSVをマスキングして出力のみ（Salesforceへの投入なし）

```bash
java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar mask-only Account accounts.csv
```

マスキング済みCSVが `Account_masked_<タイムスタンプ>.csv` として出力されます。

#### 2. mask-insert
CSVをマスキングしてSalesforceにInsert

```bash
java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar mask-insert Contact contacts.csv
```

#### 3. mask-upsert
CSVをマスキングしてSalesforceにUpsert（外部ID項目を指定。CLIで省略した場合は `Id`）

```bash
java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar mask-upsert Account accounts.csv External_Id__c
```

### 投入エラー時の出力

Insert / Upsert で失敗したレコードは `<オブジェクト名>_errors_<タイムスタンプ>.csv` に出力されます。
先頭 2 列が `RowNumber`（入力CSVのデータ行番号、ヘッダーを除き 1 始まり）と `Error`（Salesforce のエラー内容）、以降はマスキング後の項目値です。

空欄の項目は Salesforce に送信しません（数値・日付項目の型エラーや、Upsert 時に既存値を変更してしまうことを防ぐため）。

## 入力CSVフォーマット

- 文字コード: UTF-8（BOM 付きも可）。出力CSVも UTF-8 です
- 1行目: ヘッダー行（Salesforceの項目API名）
- 2行目以降: データ行
- 出力CSVの列順は入力CSVと同じです

例（accounts.csv）:
```csv
Name,Phone,BillingStreet,BillingCity,Industry
ABC Corporation,03-1234-5678,東京都渋谷区1-2-3,東京,Technology
XYZ Inc,06-9876-5432,大阪市北区4-5-6,大阪,Manufacturing
```

マスキング後:
```csv
Name,Phone,BillingStreet,BillingCity,Industry
k8jQ3mN7pL2xR5wT,090-1234-5678,456 Sample St,東京,Technology
vP9nM4hJ6sD1fG3k,090-9876-5432,789 Test Ave,大阪,Manufacturing
```

## ディレクトリ構造

```
data-masking-loader/
├── pom.xml
├── masking-config.yml
├── salesforce_sample.properties
├── src/
│   ├── main/
│       ├── java/
│       │   └── com/salesforce/tools/
│       │       ├── Launcher.java (JARのエントリポイント: 引数なし=GUI / あり=CLI)
│       │       ├── DataMaskingLoader.java (CLIメインクラス)
│       │       ├── gui/
│       │       │   ├── DataMaskingApp.java (GUIメインクラス)
│       │       │   └── MainController.java (GUIコントローラー)
│       │       ├── config/
│       │       │   └── ConfigLoader.java
│       │       ├── csv/
│       │       │   └── CsvProcessor.java
│       │       ├── masking/
│       │       │   └── MaskingDataGenerator.java
│       │       ├── model/
│       │       │   ├── MaskingConfig.java
│       │       │   ├── ObjectConfig.java
│       │       │   ├── FieldConfig.java
│       │       │   └── MaskingType.java
│       │       ├── salesforce/
│       │       │   ├── SalesforceClient.java (投入処理インタフェース)
│       │       │   ├── SalesforceConfig.java
│       │       │   ├── SalesforceConnection.java
│       │       │   └── RecordResult.java
│       │       └── service/ (CLI・GUI共通の処理本体)
│       │           ├── MaskingService.java
│       │           ├── ProcessMode.java
│       │           ├── ProcessRequest.java
│       │           ├── ProcessResult.java
│       │           └── ProcessingException.java
│       └── resources/
│           ├── fxml/
│           │   └── main.fxml (GUI画面定義)
│           ├── css/
│           │   └── style.css (GUIスタイル)
│           └── logback.xml
│   └── test/java/ (JUnit 5 単体テスト: mvn test)
└── logs/
    └── (ログファイルが出力される)
```

## トラブルシューティング

### GUI起動時のエラー

1. **JavaFXモジュールが見つからない**
   - Java 17以降を使用しているか確認
   - `mvn javafx:run` または `java -jar ...jar-with-dependencies.jar`（引数なし）で起動してください

2. **FXMLファイルが見つからない**
   - `src/main/resources/fxml/main.fxml` が存在するか確認
   - Mavenでビルドし直してください: `mvn clean package`

### 接続エラーが発生する場合

1. `salesforce.properties` のユーザー名、パスワード、セキュリティトークンが正しいか確認
2. Sandbox環境の場合、`sf.loginUrl` が `https://test.salesforce.com/...` になっているか確認
3. セキュリティトークンをパスワードに追加（パスワード+セキュリティトークン）

### マスキング設定が反映されない場合

1. `masking-config.yml` のフォーマットが正しいか確認（YAMLの構文チェック）
2. オブジェクト名、項目名がSalesforceのAPI名と一致しているか確認
3. ログファイル（`logs/data-masking-loader.log`）でエラーメッセージを確認

### CSVの読み込みエラー

1. CSVファイルのエンコーディングがUTF-8であることを確認（Excelで保存する場合は「CSV UTF-8」形式を選択）
2. ヘッダー行が存在することを確認
3. カンマ区切りになっているか確認

## ライセンス

このツールはサンプル実装です。本番環境での使用前に十分なテストを実施してください。

## 注意事項

- マスキングされたデータは元に戻せません
- 本番環境で使用する前に、必ずSandbox環境でテストしてください
- セキュリティトークンは定期的に変更されるため、エラーが発生した場合は最新のトークンを設定してください
- 大量データを処理する場合、Salesforce APIの使用量制限に注意してください（SOAP API は 200 件ごとに 1 回の API 呼び出しを消費します）
- ログは既定で INFO レベルです。マスキング後の値はログに出力しません
