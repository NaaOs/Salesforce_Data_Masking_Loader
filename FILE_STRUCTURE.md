# Salesforce Data Masking Loader - ファイル構成

## プロジェクト構造

```
data-masking-loader/
│
├── pom.xml                                 # Mavenプロジェクト設定（Java 17）
├── masking-config.yml                      # マスキング設定ファイル
├── salesforce_sample.properties            # Salesforce接続設定のひな形
├── sample-accounts.csv                     # サンプルCSVデータ
├── run-gui.sh / run-gui.bat                # ビルド・GUI起動スクリプト
├── README.md                               # 使用方法ドキュメント
├── docs/program-design.html                # プログラム設計書
│
├── src/main/
│   ├── java/com/salesforce/tools/
│   │   │
│   │   ├── Launcher.java                  # JARエントリポイント（引数なし=GUI / あり=CLI）
│   │   ├── DataMaskingLoader.java         # CLIメインクラス
│   │   │
│   │   ├── gui/
│   │   │   ├── DataMaskingApp.java        # GUIアプリケーション
│   │   │   └── MainController.java        # 画面コントローラー
│   │   │
│   │   ├── service/                       # CLI・GUI共通の処理本体
│   │   │   ├── MaskingService.java        # マスキング・出力・投入の制御
│   │   │   ├── ProcessMode.java           # 処理モード
│   │   │   ├── ProcessRequest.java        # 処理要求
│   │   │   ├── ProcessResult.java         # 処理結果（成功・失敗件数）
│   │   │   └── ProcessingException.java   # 途中中断時の例外
│   │   │
│   │   ├── config/
│   │   │   └── ConfigLoader.java          # YAML設定読み込み・検証
│   │   │
│   │   ├── csv/
│   │   │   └── CsvProcessor.java          # CSV読み書き（UTF-8、ストリーミング）
│   │   │
│   │   ├── masking/
│   │   │   └── MaskingDataGenerator.java  # マスキングデータ生成
│   │   │
│   │   ├── model/
│   │   │   ├── MaskingConfig.java         # マスキング設定モデル
│   │   │   ├── ObjectConfig.java          # オブジェクト設定モデル
│   │   │   ├── FieldConfig.java           # 項目設定モデル
│   │   │   └── MaskingType.java           # マスキングタイプ列挙型
│   │   │
│   │   └── salesforce/
│   │       ├── SalesforceClient.java      # 投入処理インタフェース
│   │       ├── SalesforceConfig.java      # 接続設定（プロパティ＋環境変数）
│   │       ├── SalesforceConnection.java  # Partner API操作（Insert/Upsert）
│   │       └── RecordResult.java          # レコード単位の投入結果
│   │
│   └── resources/
│       ├── fxml/main.fxml                  # 画面定義
│       ├── css/style.css                   # 画面スタイル
│       └── logback.xml                     # ログ設定
│
├── src/test/java/com/salesforce/tools/     # JUnit 5 単体テスト（mvn test）
│
└── logs/                                   # ログ出力ディレクトリ
    └── data-masking-loader.log
```

## ファイル説明

### 設定ファイル

- **masking-config.yml**: マスキング対象のオブジェクトと項目を定義（UTF-8）
- **salesforce.properties**: Salesforce接続情報（`.gitignore` 登録済み）。認証情報は環境変数 `SF_USERNAME` / `SF_PASSWORD` / `SF_SECURITY_TOKEN` でも指定可能（環境変数が優先）

### ソースコード

#### エントリポイント
- **Launcher.java**: 実行可能JARのメインクラス。引数の有無でGUI/CLIを切り替え
- **DataMaskingLoader.java**: CLI。引数解析と終了コード（0: 正常 / 1: エラー / 2: 一部失敗）
- **DataMaskingApp.java / MainController.java**: GUI

#### 処理本体
- **MaskingService.java**: CSVをチャンク単位でマスキングし、CSV出力またはSalesforce投入。失敗レコードはエラーCSVへ出力

#### 設定管理
- **ConfigLoader.java**: YAML設定ファイルの読み込みと検証（タイプ別の length / value 検証を含む）

#### CSV処理
- **CsvProcessor.java**: CSVファイルの読み書き（UTF-8・BOM対応・列順保持）、マスキング適用

#### マスキング
- **MaskingDataGenerator.java**: ダミーデータ生成（電話番号、メール等）

#### データモデル
- **MaskingConfig.java**: マスキング設定全体
- **ObjectConfig.java**: オブジェクト単位の設定
- **FieldConfig.java**: 項目単位の設定
- **MaskingType.java**: マスキングタイプの定義

#### Salesforce連携
- **SalesforceConfig.java**: 接続パラメータの読み込みと検証
- **SalesforceConnection.java**: Partner API操作（Insert/Upsert、ログアウト）

## ビルド後の成果物

```
target/
├── data-masking-loader-1.0.0.jar
└── data-masking-loader-1.0.0-jar-with-dependencies.jar  # 実行可能JAR
```

## 出力ファイル

```
<オブジェクト名>_masked_<yyyyMMdd_HHmmss_SSS>.csv   # mask-only の出力
<オブジェクト名>_errors_<yyyyMMdd_HHmmss_SSS>.csv   # Insert/Upsert で失敗したレコード
```

## ログファイル

```
logs/
├── data-masking-loader.log              # 当日のログ
└── data-masking-loader.2026-02-14.log   # 過去のログ（30日保持）
```
