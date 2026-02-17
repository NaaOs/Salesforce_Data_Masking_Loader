package com.salesforce.tools.gui;

import com.salesforce.tools.config.ConfigLoader;
import com.salesforce.tools.csv.CsvProcessor;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.ObjectConfig;
import com.salesforce.tools.salesforce.SalesforceConfig;
import com.salesforce.tools.salesforce.SalesforceConnection;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * メイン画面のコントローラー
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private TextField txtMaskingConfig;
    @FXML private TextField txtSalesforceConfig;
    @FXML private TextField txtInputCsv;
    @FXML private ComboBox<String> cmbObjectName;
    @FXML private ComboBox<String> cmbProcessMode;
    @FXML private TextField txtExternalIdField;
    @FXML private TextArea txtLog;
    @FXML private ProgressBar progressBar;
    @FXML private Button btnExecute;
    @FXML private Label lblStatus;

    private MaskingConfig maskingConfig;
    private File currentCsvFile;

    @FXML
    public void initialize() {
        // 処理モードの選択肢を設定
        cmbProcessMode.setItems(FXCollections.observableArrayList(
            "mask-only (マスキングのみ)",
            "mask-insert (Insert)",
            "mask-upsert (Upsert)"
        ));
        cmbProcessMode.getSelectionModel().select(0);

        // 処理モード変更時のイベント
        cmbProcessMode.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isUpsert = newVal != null && newVal.contains("upsert");
            txtExternalIdField.setDisable(!isUpsert);
        });

        txtExternalIdField.setDisable(true);
        
        // デフォルトのファイルパスを設定
        txtMaskingConfig.setText("masking-config.yml");
        txtSalesforceConfig.setText("salesforce.properties");
        
        appendLog("アプリケーション起動完了");
    }

    @FXML
    private void onBrowseMaskingConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("マスキング設定ファイルを選択");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("YAML Files", "*.yml", "*.yaml")
        );
        File file = fileChooser.showOpenDialog(txtMaskingConfig.getScene().getWindow());
        if (file != null) {
            txtMaskingConfig.setText(file.getAbsolutePath());
            loadMaskingConfig();
        }
    }

    @FXML
    private void onBrowseSalesforceConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salesforce設定ファイルを選択");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Properties Files", "*.properties")
        );
        File file = fileChooser.showOpenDialog(txtSalesforceConfig.getScene().getWindow());
        if (file != null) {
            txtSalesforceConfig.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onBrowseInputCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("入力CSVファイルを選択");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        File file = fileChooser.showOpenDialog(txtInputCsv.getScene().getWindow());
        if (file != null) {
            txtInputCsv.setText(file.getAbsolutePath());
            currentCsvFile = file;
        }
    }

    @FXML
    private void onLoadMaskingConfig() {
        loadMaskingConfig();
    }

    private void loadMaskingConfig() {
        String configPath = txtMaskingConfig.getText();
        if (configPath == null || configPath.isEmpty()) {
            showError("マスキング設定ファイルが指定されていません");
            return;
        }

        try {
            maskingConfig = ConfigLoader.loadConfig(configPath);
            if (ConfigLoader.validateConfig(maskingConfig)) {
                // オブジェクト名のリストを更新
                List<String> objectNames = maskingConfig.getObjects().stream()
                    .map(ObjectConfig::getName)
                    .toList();
                cmbObjectName.setItems(FXCollections.observableArrayList(objectNames));
                if (!objectNames.isEmpty()) {
                    cmbObjectName.getSelectionModel().select(0);
                }
                appendLog("マスキング設定を読み込みました: " + objectNames.size() + " オブジェクト");
                showInfo("設定ファイルを正常に読み込みました");
            } else {
                showError("設定ファイルの検証に失敗しました");
            }
        } catch (Exception e) {
            logger.error("設定ファイル読み込みエラー", e);
            showError("設定ファイルの読み込みに失敗しました: " + e.getMessage());
        }
    }

    @FXML
    private void onExecute() {
        // 入力チェック
        if (maskingConfig == null) {
            showError("マスキング設定を読み込んでください");
            return;
        }

        String objectName = cmbObjectName.getValue();
        if (objectName == null || objectName.isEmpty()) {
            showError("オブジェクト名を選択してください");
            return;
        }

        String csvPath = txtInputCsv.getText();
        if (csvPath == null || csvPath.isEmpty()) {
            showError("入力CSVファイルを選択してください");
            return;
        }

        String processMode = cmbProcessMode.getValue();
        if (processMode == null) {
            showError("処理モードを選択してください");
            return;
        }

        // Upsertモードの場合は外部ID項目が必須
        if (processMode.contains("upsert")) {
            String externalIdField = txtExternalIdField.getText();
            if (externalIdField == null || externalIdField.isEmpty()) {
                showError("Upsertモードでは外部ID項目の指定が必須です");
                return;
            }
        }

        // 非同期で処理を実行
        executeAsync();
    }

    private void executeAsync() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0, 100);
                updateMessage("処理開始...");

                String objectName = cmbObjectName.getValue();
                String csvPath = txtInputCsv.getText();
                String processMode = cmbProcessMode.getValue();

                try {
                    // オブジェクト設定取得
                    ObjectConfig objectConfig = maskingConfig.getObjectConfig(objectName);
                    if (objectConfig == null) {
                        throw new Exception("オブジェクト " + objectName + " の設定が見つかりません");
                    }

                    updateProgress(10, 100);
                    updateMessage("CSVファイル読み込み中...");

                    // CSV処理
                    CsvProcessor csvProcessor = new CsvProcessor();
                    List<Map<String, Object>> maskedRecords = 
                        csvProcessor.readAndMaskCsv(csvPath, objectConfig);

                    if (maskedRecords.isEmpty()) {
                        updateMessage("処理対象のレコードがありません");
                        return null;
                    }

                    updateProgress(50, 100);
                    updateMessage(maskedRecords.size() + " 件のレコードをマスキング完了");

                    // 処理モード別実行
                    if (processMode.contains("mask-only")) {
                        executeMaskOnly(csvProcessor, maskedRecords, objectName);
                    } else if (processMode.contains("mask-insert")) {
                        executeMaskInsert(maskedRecords, objectName);
                    } else if (processMode.contains("mask-upsert")) {
                        String externalIdField = txtExternalIdField.getText();
                        executeMaskUpsert(maskedRecords, objectName, externalIdField);
                    }

                    updateProgress(100, 100);
                    updateMessage("処理完了");

                } catch (Exception e) {
                    logger.error("処理中にエラーが発生", e);
                    updateMessage("エラー: " + e.getMessage());
                    throw e;
                }

                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                btnExecute.setDisable(false);
                showInfo("処理が正常に完了しました");
            }

            @Override
            protected void failed() {
                super.failed();
                btnExecute.setDisable(false);
                showError("処理中にエラーが発生しました: " + getException().getMessage());
            }
        };

        // プログレスバーとステータスをバインド
        progressBar.progressProperty().bind(task.progressProperty());
        lblStatus.textProperty().bind(task.messageProperty());

        // ログ出力
        task.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                appendLog(newVal);
            }
        });

        btnExecute.setDisable(true);
        new Thread(task).start();
    }

    private void executeMaskOnly(CsvProcessor csvProcessor, 
                                 List<Map<String, Object>> maskedRecords,
                                 String objectName) throws IOException {
        String outputPath = objectName + "_masked_" + System.currentTimeMillis() + ".csv";
        csvProcessor.writeMaskedCsv(outputPath, maskedRecords);
        Platform.runLater(() -> 
            appendLog("マスキング済みCSVファイル: " + outputPath)
        );
    }

    private void executeMaskInsert(List<Map<String, Object>> maskedRecords,
                                   String objectName) throws Exception {
        SalesforceConfig sfConfig = loadSalesforceConfig();
        SalesforceConnection sfConnection = new SalesforceConnection(sfConfig);
        
        try {
            Platform.runLater(() -> appendLog("Salesforceに接続中..."));
            sfConnection.connect();
            Platform.runLater(() -> appendLog("Insert実行中..."));
            sfConnection.insertRecords(objectName, maskedRecords);
        } finally {
            sfConnection.disconnect();
        }
    }

    private void executeMaskUpsert(List<Map<String, Object>> maskedRecords,
                                   String objectName,
                                   String externalIdField) throws Exception {
        SalesforceConfig sfConfig = loadSalesforceConfig();
        SalesforceConnection sfConnection = new SalesforceConnection(sfConfig);
        
        try {
            Platform.runLater(() -> appendLog("Salesforceに接続中..."));
            sfConnection.connect();
            Platform.runLater(() -> appendLog("Upsert実行中..."));
            sfConnection.upsertRecords(objectName, maskedRecords, externalIdField);
        } finally {
            sfConnection.disconnect();
        }
    }

    private SalesforceConfig loadSalesforceConfig() throws IOException {
        String configPath = txtSalesforceConfig.getText();
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            props.load(fis);
        }

        SalesforceConfig config = new SalesforceConfig();
        config.setUsername(props.getProperty("sf.username"));
        config.setPassword(props.getProperty("sf.password"));
        config.setSecurityToken(props.getProperty("sf.securityToken", ""));
        config.setLoginUrl(props.getProperty("sf.loginUrl", 
                          "https://login.salesforce.com/services/Soap/u/58.0"));
        
        String batchSizeStr = props.getProperty("sf.batchSize", "200");
        config.setBatchSize(Integer.parseInt(batchSizeStr));

        return config;
    }

    @FXML
    private void onClearLog() {
        txtLog.clear();
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );
            txtLog.appendText(timestamp + " - " + message + "\n");
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("エラー");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
            appendLog("ERROR: " + message);
        });
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("情報");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
            appendLog("INFO: " + message);
        });
    }
}
