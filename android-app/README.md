# RICOH Live Streaming Client Sample APP

Android スマートフォンで RICOH Live Streaming API に接続するアプリ

## 動かし方

1. Android Studio で ClientSDK のルートディレクトリごとインポートする。
2. Client ID, Secret, Room ID を取得して、設定ファイルを作成する。
3. ビルドして Android スマートフォン上で起動する。

### アクティビティ

* BidirActivity: 映像・音声の双方向送受信を行う
* RecvActivity: 映像の受信、音声の双方向送受信を行う
* FileSenderActivity: 録画済みファイルを映像として送信する
* UvcCameraActivity: 外付けカメラの映像・音声の双方向送受信を行う
* ScreenShareActivity: 画面共有映像の送信、映像の受信、音声の双方向送受信を行う

### 設定ファイル

* 以下の書式で `android-app/local.properties` を作成する。
  * `client_id` と `client_secret` は実際の値を入れる

```
client_id=3341e140-0290-43f5-95a0-bd9f98d8ecdc
client_secret=kxiFVi6lzf14dffq3fg46ghg7dip1ash74ioisudsensJ9fe89f4fjijoiafDVcNmg
room_id=sample-room
video_bitrate=5000
```

## ログ出力

本アプリでは以下のログを出力する。

* Clientログ
  * `/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/app/` に出力
* libwebrtcログ
  * `/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/libwebrtc/` に出力
* statsログ
  * `/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/stats/` に出力

以下のコマンドで本体ディスク上のログファイルをすべて取得できる。

```sh
$ adb pull /storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs
```

### Clientログ

ログ出力には SLF4J を使用する。つまりアプリはSLF4Jに対応したログ実装を指定する必要がある。

実装としては [logback-android](https://github.com/tony19/logback-android) を推奨する。logback-android は XML ファイルによりログ出力を細やかに設定可能であり、ファイル等への出力もできる。
ログ出力の設定は `android-app/src/main/assets/logback.xml` で指定可能。　

ログ出力レベルは libwebrtc のログ出力仕様 に倣って `ERROR` / `WARNING` / `INFO` / `TRACE` の 4 段階で設定可能。

設定レベルより上位レベルのログも出力される。つまり TRACE レベルに設定するとすべてのログが出力される。そして最上位の ERROR レベルのログは常に出力される。

アプリログは `/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/app/20221215T143500_app.log` という名前で出力される。  
ファイル名は実際の日時で `yyyyMMdd'T'HHmmss_app.log` の形式となる。

### libwebrtcログ

`Option.Builder#loggingSeverity()` で logcat に出力するログレベルの指定が可能。

また、`Client#setLibWebrtcLogOption()` を利用することで libwebrtc ログも本体のディスク上に `/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/libwebrtc/webrtc_log_0` という名前で出力することができる。

libwebrtc ログは接続する度に "webrtc" プレフィックスのログファイルを削除して再作成する仕組みのため、  
接続時に過去実行時の webrtc ログを残すために `/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/libwebrtc/20221215T143500_webrtc_log_0` という名前のファイルにリネームしている。  
リネーム後のファイル名は実際の日時で `yyyyMMdd'T'HHmmss_webrtc_log_{0始まりの連番}` の形式となる。

### statsログ

`RTCStats` を本体のディスク上に書き込む機能がある。

`/storage/emulated/0/Android/data/com.ricoh.livestreaming.app/files/logs/stats/20190129T1629.log` という名前で出力される。
ファイル名は実際の日時で `yyyyMMdd'T'HHmm` の形式となる。  
このログファイルも接続する度に新しいファイルが生成される。

ファイル形式は [LTSV](http://ltsv.org/) となっている。

すべての情報を出力しているのではなく `candidate-pair`, `outbound-rtp`, `inbound-rtp`, `remote-inbound-rtp`, `track`, `sender`, `media-source`, `local-candidate`, `remote-candidate` の情報だけ出力している。

その他の情報を出力したい場合は `RTCStatsLogger.kt` を修正する。
出力可能な情報の一覧は https://www.w3.org/TR/webrtc-stats/ で確認できるが、
libwebrtc の実装に依存するため、記載されているすべての情報が出力できるとは限らない。
