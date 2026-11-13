package com.salesforce.tools.gui;

import com.salesforce.tools.config.ConfigLoader;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.ObjectConfig;
import com.salesforce.tools.service.MaskingService;
import com.salesforce.tools.service.ProcessMode;
import com.salesforce.tools.service.ProcessRequest;
import com.salesforce.tools.service.ProcessResult;
import com.salesforce.tools.service.ProcessingException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * メイン画面のコントローラー
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private TextField txtMaskingConfig;
    @FXML private TextField txtSalesforceConfig;
    @FXML private TextField txtInputCsv;
    @FXML private ComboBox<String> cmbObjectName;
    @FXML private ComboBox<ProcessMode> cmbProcessMode;
    @FXML private TextField txtExternalIdField;
    @FXML private TextArea txtLog;
    @FXML private ProgressBar progressBar;
    @FXML private Button btnExecute;
    @FXML private Label lblStatus;

    private MaskingConfig maskingConfig;

    @FXML
    public void initialize() {
        cmbProcessMode.setItems(FXCollections.observableArrayList(ProcessMode.values()));
        cmbProcessMode.getSelectionModel().select(ProcessMode.MASK_ONLY);
        cmbProcessMode.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> txtExternalIdField.setDisable(newVal != ProcessMode.MASK_UPSERT));
        txtExternalIdField.setDisable(true);

        txtMaskingConfig.setText("masking-config.yml");
        txtSalesforceConfig.setText("salesforce.properties");

        appendLog("アプリケーション起動完了");
    }

    @FXML
    private void onBrowseMaskingConfig() {
        File file = chooseFile("マスキング設定ファイルを選択", "YAML Files", "*.yml", "*.yaml");
        if (file != null) {
            txtMaskingConfig.setText(file.getAbsolutePath());
            loadMaskingConfig();
        }
    }

    @FXML
    private void onBrowseSalesforceConfig() {
        File file = chooseFile("Salesforce設定ファイルを選択", "Properties Files", "*.properties");
        if (file != null) {
            txtSalesforceConfig.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onBrowseInputCsv() {
        File file = chooseFile("入力CSVファイルを選択", "CSV Files", "*.csv");
        if (file != null) {
            txtInputCsv.setText(file.getAbsolutePath());
        }
    }

    private File chooseFile(String title, String filterName, String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, extensions));
        return fileChooser.showOpenDialog(txtLog.getScene().getWindow());
    }

    @FXML
    private void onLoadMaskingConfig() {
        loadMaskingConfig();
    }

    private void loadMaskingConfig() {
        String configPath = txtMaskingConfig.getText();
        if (configPath == null || configPath.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "マスキング設定ファイルが指定されていません");
            return;
        }

        try {
            MaskingConfig loaded = ConfigLoader.loadConfig(configPath);
            if (!ConfigLoader.validateConfig(loaded)) {
                showAlert(Alert.AlertType.ERROR, "設定ファイルの検証に失敗しました（詳細はログファイルを参照）");
                return;
            }
            maskingConfig = loaded;
            List<String> objectNames = maskingConfig.getObjects().stream().map(ObjectConfig::getName).toList();
            cmbObjectName.setItems(FXCollections.observableArrayList(objectNames));
            if (!objectNames.isEmpty()) {
                cmbObjectName.getSelectionModel().select(0);
            }
            appendLog("マスキング設定を読み込みました: " + objectNames.size() + " オブジェクト");
            showAlert(Alert.AlertType.INFORMATION, "設定ファイルを正常に読み込みました");
        } catch (Exception e) {
            logger.error("設定ファイル読み込みエラー", e);
            showAlert(Alert.AlertType.ERROR, "設定ファイルの読み込みに失敗しました: " + e.getMessage());
        }
    }

    @FXML
    private void onExecute() {
        // 入力値は UI スレッドでここで取得し、バックグラウンド処理には値として渡す
        if (maskingConfig == null) {
            showAlert(Alert.AlertType.ERROR, "マスキング設定を読み込んでください");
            return;
        }
        String objectName = cmbObjectName.getValue();
        if (objectName == null || objectName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "オブジェクト名を選択してください");
            return;
        }
        String csvPath = txtInputCsv.getText();
        if (csvPath == null || csvPath.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "入力CSVファイルを選択してください");
            return;
        }
        ProcessMode mode = cmbProcessMode.getValue();
        if (mode == null) {
            showAlert(Alert.AlertType.ERROR, "処理モードを選択してください");
            return;
        }
        String externalIdField = txtExternalIdField.getText();
        if (mode == ProcessMode.MASK_UPSERT && (externalIdField == null || externalIdField.isBlank())) {
            showAlert(Alert.AlertType.ERROR, "Upsertモードでは外部ID項目の指定が必須です");
            return;
        }

        ProcessRequest request = new ProcessRequest(mode, objectName, csvPath.trim(),
                mode == ProcessMode.MASK_UPSERT ? externalIdField.trim() : null,
                txtSalesforceConfig.getText(), Path.of("."));
        executeAsync(new MaskingService(maskingConfig), request);
    }

    private void executeAsync(MaskingService service, ProcessRequest request) {
        Task<ProcessResult> task = new Task<>() {
            @Override
            protected ProcessResult call() throws Exception {
                updateProgress(0, 1);
                updateMessage("処理開始...");
                return service.execute(request, (fraction, message) -> {
                    updateProgress(fraction, 1);
                    updateMessage(message);
                });
            }
        };

        task.setOnSucceeded(e -> {
            finishTask();
            ProcessResult result = task.getValue();
            appendLog("結果: " + result.summary().replace("\n", " / "));
            if (result.hasFailures()) {
                showAlert(Alert.AlertType.WARNING, result.failureCount()
                        + " 件のレコードの投入に失敗しました。\nエラーファイルで原因を確認してください。\n\n" + result.summary());
            } else if (result.totalRecords() == 0) {
                showAlert(Alert.AlertType.INFORMATION, "処理対象のレコードがありません");
            } else {
                showAlert(Alert.AlertType.INFORMATION, "処理が正常に完了しました\n\n" + result.summary());
            }
        });
        task.setOnFailed(e -> {
            finishTask();
            Throwable ex = task.getException();
            logger.error("処理中にエラーが発生", ex);
            String message = "処理中にエラーが発生しました: " + ex.getMessage();
            if (ex instanceof ProcessingException pe && pe.getPartialResult() != null) {
                message += "\n\n中断までの結果:\n" + pe.getPartialResult().summary();
            }
            showAlert(Alert.AlertType.ERROR, message);
        });

        progressBar.progressProperty().bind(task.progressProperty());
        lblStatus.textProperty().bind(task.messageProperty());
        task.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                appendLog(newVal);
            }
        });

        btnExecute.setDisable(true);
        Thread thread = new Thread(task, "masking-task");
        thread.setDaemon(true);
        thread.start();
    }

    /** UI スレッドで呼ばれる（Task のイベントハンドラ） */
    private void finishTask() {
        btnExecute.setDisable(false);
        progressBar.progressProperty().unbind();
        lblStatus.textProperty().unbind();
    }

    @FXML
    private void onClearLog() {
        txtLog.clear();
    }

    private void appendLog(String message) {
        runOnFxThread(() -> txtLog.appendText(LocalDateTime.now().format(LOG_TIME) + " - " + message + "\n"));
    }

    private void showAlert(Alert.AlertType type, String message) {
        runOnFxThread(() -> {
            String prefix = switch (type) {
                case ERROR -> "ERROR: ";
                case WARNING -> "WARN: ";
                default -> "INFO: ";
            };
            appendLog(prefix + message.replace("\n", " "));
            Alert alert = new Alert(type);
            alert.setTitle(switch (type) {
                case ERROR -> "エラー";
                case WARNING -> "警告";
                default -> "情報";
            });
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
