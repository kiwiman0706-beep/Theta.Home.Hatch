# THETA Home Hatch

画面の端のタブでウィジェットや道具を切り替え、**背景（デスクトップ）にはファイラーを置く** Android のホームアプリ。
スマホはアプリには行きやすいのにファイルには行きにくいので、PC のデスクトップのように「ファイルの置き場」をホームにする。ただ散らかして置くのではなく、ファイラーとして整理された形で置く。

- 版：**0.1.1-alpha（versionCode 2）**／package `com.theta.homehatch`／Android 8.0（API 26）以上
- 対象：Galaxy Z Fold5（カバー・メイン）、Alldocube iPlay 60 / 80 Pro 系、Unihertz Titan 2
- 土台：ChatGPT で作った 0.1.0-alpha（ネイティブ Java、Android 標準の View）。0.1.1 はそれを標準の Gradle 手順でビルドし直したもので、**機能は 0.1.0 と同じ**
- **実機での確認はまだです。** 画面は JVM 上で描いて確かめています（`./gradlew :app:testDebugUnitTest` → `app/build/screenshots/`）

使い方は [はじめに.txt](はじめに.txt)、ChatGPT 版の説明は [docs/CHATGPT_README.md](docs/CHATGPT_README.md)、これからの計画は [docs/PLAN.md](docs/PLAN.md)。

## 入れ方

ChatGPT 版 0.1.0 とは署名の鍵が違うため、**上書きできません。0.1.0 を一度アンインストールしてから**入れてください（アプリ内の録音・手書きなどの記録も消えるので、必要なものは先に「書き出す」）。

## ビルド

`./gradlew :app:assembleDebug`（Android SDK Platform 36 / Build Tools 35.0.0、Java 17 以上）。

- テスト・lint・画面の見本：`./gradlew :app:testDebugUnitTest :app:lintDebug`
- リリース署名：リポジトリの外に置いた鍵の情報を `keystore.properties`（gitignore 済み）に書いて `./gradlew :app:assembleRelease`。**鍵と鍵の情報はコミットしない。**

## 構成

| 場所 | 内容 |
| --- | --- |
| `app/src/main/java/com/theta/homehatch/MainActivity.java` | ホーム・ファイラー・タブ・ドロワー・記録・ウィジェット |
| `…/RecordingService.java` | 録音（通知に停止ボタン） |
| `…/CaptureProvider.java` | 記録ファイルをほかのアプリへ渡す |
| `reference/` | ChatGPT 版の調査メモ（参考動画 4 本の分析）とモックアップ |
