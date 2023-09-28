# RICOH Live Streaming Client SDK for Android

株式会社リコーが提供するRICOH Live Streaming Serviceを利用するためのRICOH Live Streaming Client SDK for Android です。

RICOH Live Streaming Serviceは、映像/音声などのメディアデータやテキストデータなどを
複数の拠点間で双方向かつリアルタイムにやりとりできるプラットフォームです。

サービスのご利用には、API利用規約への同意とアカウントの登録、ソフトウェア利用許諾書への同意が必要です。
詳細は下記Webサイトをご確認ください。

* サービスサイト: https://livestreaming.ricoh/
* ソフトウェア開発者向けサイト: https://api.livestreaming.ricoh/
* アカウント登録: https://console.livestreaming.mw.smart-integration.ricoh.com/login/register
* ソフトウェア使用許諾契約書 : [Software License Agreement](SoftwareLicenseAgreement.txt)

* NOTICE: This package includes SDK and sample application(s) for "RICOH Live Streaming Service".
At this moment, we provide API license agreement / software license agreement only in Japanese.

## プロジェクト構成

* [libs](libs) (ライブラリ本体)
* [android-app](android-app) RICOH Live Streaming API の Android スマートフォン向けサンプル
* [theta-plugin](https://github.com/ricoh-live-streaming-api/android-sdk-samples/tree/main/android-device-samples/theta-plugin) RICOH Live Streaming API の THETA プラグインサンプル (配信専用) はこちら

## 依存ライブラリ

Client SDK は以下のライブラリを使用しています。

* org.slf4j:slf4j-api:2.0.7
* com.squareup.okhttp3:okhttp:4.11.0
* com.google.code.gson:gson:2.10.1
* com.theta360:pluginlibrary:3.2.0

## バージョンアップ時の更新方法

client-sdk-{バージョン}.aar を差し替えてください
