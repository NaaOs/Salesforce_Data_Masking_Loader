#!/bin/bash

# Data Masking Loader GUI起動スクリプト

echo "===== Data Masking Loader GUI ====="
echo ""

# ビルド
build() {
    echo "プロジェクトをビルド中..."
    mvn clean package
    
    if [ $? -eq 0 ]; then
        echo "ビルド成功"
    else
        echo "ビルド失敗"
        exit 1
    fi
}

# GUI起動
run_gui() {
    echo "GUIアプリケーションを起動中..."
    mvn javafx:run
}

# メイン処理
case "$1" in
    build)
        build
        ;;
    run)
        run_gui
        ;;
    build-and-run)
        build
        if [ $? -eq 0 ]; then
            run_gui
        fi
        ;;
    *)
        echo "使い方:"
        echo "  $0 build           # ビルドのみ"
        echo "  $0 run             # GUIを起動"
        echo "  $0 build-and-run   # ビルドしてGUI起動"
        echo ""
        echo "例:"
        echo "  $0 build-and-run"
        exit 1
        ;;
esac
