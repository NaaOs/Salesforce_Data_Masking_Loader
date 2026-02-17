package com.salesforce.tools.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * データマスキングローダー GUIアプリケーション
 */
public class DataMaskingApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DataMaskingApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            
            Scene scene = new Scene(root, 900, 700);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            
            primaryStage.setTitle("Salesforce Data Masking Loader");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();
            
            logger.info("GUI起動完了");
            
        } catch (Exception e) {
            logger.error("GUI起動エラー", e);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
