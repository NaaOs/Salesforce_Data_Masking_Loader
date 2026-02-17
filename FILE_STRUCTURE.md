# Salesforce Data Masking Loader - ファイル構成

## プロジェクト構造

```
data-masking-loader/
│
├── pom.xml                                 # Mavenプロジェクト設定
├── masking-config.yml                      # マスキング設定ファイル
├── salesforce.properties.sample            # Salesforce接続設定サンプル
├── sample-accounts.csv                     # サンプルCSVデータ
├── run.sh                                  # ビルド・実行スクリプト
├── README.md                               # 使用方法ドキュメント
│
├── src/main/
│   ├── java/com/salesforce/tools/
│   │   │
│   │   ├── DataMaskingLoader.java         # メインクラス
│   │   │
│   │   ├── config/
│   │   │   └── ConfigLoader.java          # YAML設定読み込み
│   │   │
│   │   ├── csv/
│   │   │   └── CsvProcessor.java          # CSV処理・マスキング実行
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
│   │       ├── SalesforceConfig.java      # Salesforce接続設定
│   │       └── SalesforceConnection.java  # Salesforce API操作
│   │
│   └── resources/
│       └── logback.xml                     # ログ設定
│
└── logs/                                   # ログ出力ディレクトリ
    └── data-masking-loader.log
```

## ファイル説明

### 設定ファイル

- **masking-config.yml**: マスキング対象のオブジェクトと項目を定義
- **salesforce.properties**: Salesforce接続情報（ユーザー名、パスワード等）

### ソースコード

#### メイン
- **DataMaskingLoader.java**: エントリーポイント、処理フロー制御

#### 設定管理
- **ConfigLoader.java**: YAML設定ファイルの読み込みと検証

#### CSV処理
- **CsvProcessor.java**: CSVファイルの読み書き、マスキング適用

#### マスキング
- **MaskingDataGenerator.java**: ダミーデータ生成（電話番号、メール等）

#### データモデル
- **MaskingConfig.java**: マスキング設定全体
- **ObjectConfig.java**: オブジェクト単位の設定
- **FieldConfig.java**: 項目単位の設定
- **MaskingType.java**: マスキングタイプの定義

#### Salesforce連携
- **SalesforceConfig.java**: 接続パラメータ
- **SalesforceConnection.java**: Partner API操作（Insert/Upsert）

## ビルド後の成果物

```
target/
├── data-masking-loader-1.0.0.jar
└── data-masking-loader-1.0.0-jar-with-dependencies.jar  # 実行可能JAR
```

## ログファイル

```
logs/
├── data-masking-loader.log              # 当日のログ
└── data-masking-loader.2026-02-14.log   # 過去のログ（30日保持）
```
