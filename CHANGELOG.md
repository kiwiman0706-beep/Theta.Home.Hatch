# 変更履歴

## 0.1.1-alpha（2026-10-23）

ChatGPT 版 0.1.0-alpha を土台に戻し、標準の Gradle 手順でビルドし直した版。アプリの機能は 0.1.0 と同じ。

- 0.1.0 で外されていた通常の Java コンパイル手順を戻し、`gradlew :app:assembleRelease` だけで作れるようにした
- リリース用の署名鍵をリポジトリの外から読む設定（`keystore.properties`）
- 画面の見本を JVM 上で描くテスト（Fold5 カバー・メイン、タブレット、Titan）と CI
- versionCode 2 / versionName 0.1.1-alpha。0.1.0 とは鍵が違うため、入れ替えにはアンインストールが必要

## 0.2.0-alpha（2026-10-23、取り下げ）

Kotlin + Jetpack Compose で作り直した版。ChatGPT 版に寄せる方針になったため本線から外した。コミット `e9728b4` に残っている（端からのスライドイン、Hatch のインストール、通知からの LINE・メール表示など、これから移植する部品の参考）。

## 0.1.0-alpha（2026-10-22、ChatGPT 版）

最初の版。詳細は docs/CHATGPT_README.md。
