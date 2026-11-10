# PC から adb で試す（設定の変更・β テスト）

PC の Claude Code（ローカル）が、USB か Wi-Fi でつないだ端末の Home を確かめたり、設定を変えたりするための手順。
クラウドのスレッドからは端末に届かないので、**PC の Claude Code に任せる**か、PC で出た結果をスレッドに貼る。

## 1. つなぐ（初回だけ）

1. 端末：設定 → 端末情報 → ソフトウェア情報 →「ビルド番号」を 7 回押す（開発者向けオプションが出る）
2. 端末：設定 → 開発者向けオプション →「USB デバッグ」をオン（Wi-Fi なら「ワイヤレス デバッグ」をオン）
3. PC：adb を入れる
   - Windows：`winget install Google.PlatformTools`
   - Mac：`brew install android-platform-tools`
   - Android Studio が入っていれば、SDK の `platform-tools` にもある
4. USB：ケーブルでつなぎ、端末の「USB デバッグを許可」で OK。`adb devices` に出れば完了
5. Wi-Fi：ワイヤレス デバッグ →「ペア設定コードによるデバイスのペア設定」に出る IP:ポートとコードで
   `adb pair 192.168.x.x:ポート` → `adb connect 192.168.x.x:ポート`（ワイヤレス デバッグの画面の IP アドレスとポート）

## 2. よく使う操作

```sh
# 画面を撮る（PC に保存）
adb exec-out screencap -p > home.png

# 版・画面の大きさ・テーマ
adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd info

# 設定を全部見る（秘密は出ない）。長いときはファイルを取る
adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd dump
adb pull /sdcard/Android/data/app.thetahatch.home/files/debug/prefs.json

# 1 つ読む・変える・消す（変えたら restart で反映）
adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd get --es key theme
adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd set --es key theme --es value pop
adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd remove --es key weatherDetail
adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd restart

# 落ちたとき・動きの記録
adb logcat -s ThetaHomeDebug AndroidRuntime

# APK を入れる（Releases の正式版・β 版。共通の鍵なので上書きできる）
adb install -r ThetaHomeHatch-1.4.0-beta.1.apk
```

- `set` の型は今の値から決める。新しい項目は `--es type bool|int|long|float|string` で指定
- 秘密（Gemini のキー・NAS のパスワード・トークン）は読めず、書けない
- この受け口は `android.permission.DUMP` で守っている。adb の shell からしか送れず、ほかのアプリからは使えない
- 手元でビルドした debug 版は鍵が違うので、入っている Home に上書きできない。試すのは CI の β 版で

## 3. β 版

- 版に `-` を付けて Actions の release を実行すると、プレリリース（β）になる（例 `1.4.0-beta.1`）
- versionCode は正式版の手前の数にする（1.4.0 が 140 なら、beta.1 は 139 など。1.4.0 を上書きできるように）
- 端末：設定 → UPDATE →「このホームの試験版（β）も受け取る」をオンにすると、UPDATE に β が出る。ほかのシリーズのアプリは正式版だけ
- 正式版が出たら β より新しいとみなす（1.4.0-beta.2 < 1.4.0）

## 4. PC の Claude Code に頼むとき

リポジトリを clone したフォルダで Claude Code を開き、例えば次のように頼む。

- 「adb devices でつながっているか見て、Home の画面を撮って、見づらいところを挙げて」
- 「theme を soft にして restart して、ホームの画面を撮って比べて」
- 「logcat を見ながら、すぐ記録の録るを押して落ちないか確かめて」

結果（画面・ログ）を、このリポジトリのスレッドに渡せば直せる。
