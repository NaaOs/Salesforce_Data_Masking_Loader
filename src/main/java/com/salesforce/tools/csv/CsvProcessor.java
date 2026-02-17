package com.salesforce.tools.csv;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.salesforce.tools.masking.MaskingDataGenerator;
import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.ObjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * CSVファイルの読み込み・書き込み・マスキング処理
 */
public class CsvProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CsvProcessor.class);
    private final MaskingDataGenerator maskingGenerator;

    public CsvProcessor() {
        this.maskingGenerator = new MaskingDataGenerator();
    }

    /**
     * CSVファイルを読み込んでマスキング処理を実行
     * 
     * @param inputCsvPath 入力CSVファイルパス
     * @param objectConfig マスキング設定
     * @return マスキングされたレコードのリスト
     */
    public List<Map<String, Object>> readAndMaskCsv(String inputCsvPath, 
                                                     ObjectConfig objectConfig) throws IOException {
        logger.info("CSVファイル読み込み開始: {}", inputCsvPath);
        
        List<Map<String, Object>> maskedRecords = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(inputCsvPath))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                logger.error("CSVファイルが空です");
                return maskedRecords;
            }

            logger.info("ヘッダー: {}", Arrays.toString(headers));

            String[] line;
            int recordCount = 0;
            
            while ((line = reader.readNext()) != null) {
                recordCount++;
                Map<String, Object> record = new HashMap<>();
                
                for (int i = 0; i < headers.length; i++) {
                    String fieldName = headers[i].trim();
                    String value = i < line.length ? line[i] : "";
                    
                    // マスキング対象項目かチェック
                    FieldConfig fieldConfig = objectConfig.getFieldConfig(fieldName);
                    
                    if (fieldConfig != null) {
                        // マスキング実行
                        String maskedValue = maskingGenerator.generateMaskedValue(fieldConfig);
                        record.put(fieldName, maskedValue);
                        logger.debug("項目マスキング: {} -> {}", fieldName, maskedValue);
                    } else {
                        // マスキング対象外はそのまま
                        record.put(fieldName, value);
                    }
                }
                
                maskedRecords.add(record);
                
                if (recordCount % 1000 == 0) {
                    logger.info("{}件処理完了", recordCount);
                }
            }
            
            logger.info("CSV読み込み完了: {} 件のレコード", recordCount);
            
        } catch (CsvValidationException e) {
            logger.error("CSV解析エラー", e);
            throw new IOException("CSVフォーマットエラー", e);
        }
        
        return maskedRecords;
    }

    /**
     * マスキング済みデータをCSVファイルに書き込み
     * 
     * @param outputCsvPath 出力CSVファイルパス
     * @param records レコードのリスト
     */
    public void writeMaskedCsv(String outputCsvPath, 
                              List<Map<String, Object>> records) throws IOException {
        if (records == null || records.isEmpty()) {
            logger.warn("書き込むレコードがありません");
            return;
        }

        logger.info("マスキング済みCSVファイル出力開始: {}", outputCsvPath);

        try (CSVWriter writer = new CSVWriter(new FileWriter(outputCsvPath))) {
            // ヘッダー取得（最初のレコードのキー）
            Set<String> fieldNames = records.get(0).keySet();
            String[] headers = fieldNames.toArray(new String[0]);
            writer.writeNext(headers);

            // データ行
            for (Map<String, Object> record : records) {
                String[] values = new String[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    Object value = record.get(headers[i]);
                    values[i] = value != null ? value.toString() : "";
                }
                writer.writeNext(values);
            }

            logger.info("CSV出力完了: {} 件のレコード", records.size());
        }
    }

    /**
     * CSVファイルを読み込む（マスキングなし）
     */
    public List<Map<String, Object>> readCsv(String csvPath) throws IOException {
        logger.info("CSVファイル読み込み: {}", csvPath);
        
        List<Map<String, Object>> records = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                logger.error("CSVファイルが空です");
                return records;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, Object> record = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String fieldName = headers[i].trim();
                    String value = i < line.length ? line[i] : "";
                    record.put(fieldName, value);
                }
                records.add(record);
            }
            
            logger.info("CSV読み込み完了: {} 件", records.size());
            
        } catch (CsvValidationException e) {
            logger.error("CSV解析エラー", e);
            throw new IOException("CSVフォーマットエラー", e);
        }
        
        return records;
    }
}
