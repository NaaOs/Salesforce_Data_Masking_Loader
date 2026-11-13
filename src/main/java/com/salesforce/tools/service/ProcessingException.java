package com.salesforce.tools.service;

/**
 * 処理途中で中断したことを表す例外。中断までに処理した結果を保持する。
 */
public class ProcessingException extends Exception {
    private final transient ProcessResult partialResult;

    public ProcessingException(String message, ProcessResult partialResult, Throwable cause) {
        super(message, cause);
        this.partialResult = partialResult;
    }

    /** 中断までの処理結果（Salesforce に反映済みの件数など） */
    public ProcessResult getPartialResult() {
        return partialResult;
    }
}
