package com.salesforce.tools;

import com.salesforce.tools.gui.DataMaskingApp;

/**
 * 実行可能 JAR のエントリポイント。
 *
 * <ul>
 *   <li>引数なし: GUI を起動</li>
 *   <li>引数あり: CLI（{@link DataMaskingLoader}）として実行</li>
 * </ul>
 *
 * <p>Application を継承しないクラスから JavaFX を起動することで、依存込み JAR を
 * {@code java -jar} で直接実行した場合の「JavaFX runtime components are missing」エラーも回避する。</p>
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            DataMaskingApp.main(args);
        } else {
            DataMaskingLoader.main(args);
        }
    }
}
