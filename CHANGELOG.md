# CHANGELOG

## v4.1.0
* API 変更
  * AdaptiveSendingOption の mode に BEST_RESOLUTION と BEST_FRAMERATE を追加しました。それぞれ以下のようなモードになります
    * BEST_RESOLUTION: 可能な限り 4K 解像度を優先する
    * BEST_FRAMERATE: 可能な限りフレームレートを優先する
* SDK 修正
  * THETA Z1 で takePicture を実行するとライブ映像の ShootingMode と StichingMode が変更されてしまうことがある問題を修正しました
  * Camera2VideoCapturer の takePicture 呼び出しで内部エラーが起きることがある問題を修正しました

## v4.0.1
* SDK 修正
  * 正常終了時に 53004 ConnectionClosedByServer が発生してしまうことがある問題を修正しました

## v4.0.0
* API 変更
  * Camera2VideoCapturer、UvcVideoCapturer、ThetaCameraCapturer、ThetaXCameraCapturer に pauseVideo、resumeVideo の API を追加しました
    * pauseVideo で映像が一時停止し resumeVideo で再開します
    * 内部的には pauseVideo が呼ばれたら映像フレームを保存し、resumeVideo が呼ばれるまで設定された framerate で保存したフレームを送信し続けます
  * Camera2VideoCapturer に takePicture()を追加しました。ThetaCameraCapturer と同様に静止画撮影ができます
  * NetworkErrorに SFU が WebRTC 接続を切断判定した場合のエラー 53004 ConnectionClosedByServer を追加しました
  * 破壊的変更 (β) ある connection から見て自分が送信している映像を受信している対向 connection の有無をイベントで通知するようになりました
    * Client.Listener#onUpdateConnectionsStatus()を実装する必要があります
    * 対向の入室や受信モードなどに応じて有無は変化します
    * updateconnectionsstatus イベントの video.receiver_existence で判断できるようになります
  * (β) connect の options の iceServersProtocol に TCP_TLS が指定できるようになりました。TURN の TCP か TLS が自動で選択されるようになります
* SDK 修正
  * THETA の機種によって対応していない ShootingMode が指定された際に例外を発生させるように修正しました
  * ログの ICE (WebRTC の接続試行) 関連の情報を強化しました
  * P2PRoom への connect 時に対向 connection が退室すると InternalError が発生することがある問題を修正しました

## v3.1.0
* API 変更
  * SDK が libwebrtc とは別に独自にネットワークの状況変化に応じて自動でビデオ解像度とフレームレートを変更するモードを変更する Client#changeAdaptiveSendingMode() を追加しました (β)
    * AdaptiveSendingOption#mode を BALANCED にすると SDK がネットワークの状況変化に応じて自動でビデオ解像度とフレームレートを変更します
      * 設定前に Client#setAdaptiveSendingCapturer() で Client に capturer を設定する必要があります
  * TakePictureOptions に解像度を指定するオプションを追加しました
    * JpegFileFormat の enum で指定できます
    * デフォルト値および指定できる値は THETA の機種によって異なります
* SDK 修正
  * `ThetaXCameraCapturer#setStreamingZenithCorrection()` の設定が `ThetaXCameraCapturer#takePicture()` 後に解除されてしまう問題を修正しました

## v3.0.1
* 共通修正
  * compileSdkVersion と targetSdkVersion を`31`から`33`に更新しました
* サンプルアプリ修正
  * 依存ライブラリバージョンを以下に更新しました。メジャーバージョンのアップデートを含みます
    * org.slf4j:slf4j-api:2.0.7
    * com.squareup.okhttp3:okhttp:4.11.0
    * com.google.code.gson:gson:2.10.1

## v3.0.0
* API 変更
  * USB 外付けカメラを UVC で制御する UvcControl クラスを追加しました
    * UvcControl クラスは UvcVideoCapturer#getControl(UvcControlId uvcControlId)で取得できます
    * UvcControlId は CameraTerminalUvcControlId と ProcessingUnitUvcControlId の enum から取得できます
  * **破壊的変更** com.ricoh.livestreaming.uvc パッケージから throw される USB 関連の例外クラスを com.ricoh.livestreaming.uvc.UsbException に変更しました
  * **破壊的変更** Theta(X)CameraCapturer#takePicture() の引数に TakePictureOptions を追加しました
    * TakePictureOptions は zenithCorrectionEnabled のみが設定できます。静止画撮影時の天頂補正の有効無効が制御されます
  * **破壊的変更** ThetaXCameraCapturer#setZenithCorrection() を ThetaXCameraCapturer#setStreamingZenithCorrection() に変更しました
    * 静止画撮影ではなくストリーミングのみの天頂補正の制御であることを、明示するメソッド名にしました。挙動に変更はありません
  * 破壊的変更の移行については[本変更に伴う移行ガイド](https://github.com/ricoh-live-streaming-api/android-sdk/blob/main/Guide.md#v300)を追加しましたので、ご確認ください

## v2.1.2
* SDK 修正
  * H.264 の映像受信時に解像度が変更されるとフレームレートが低下し続けることがある問題を修正しました
  * THETA X でカメラのつなぎ目の部分が粗くなる問題を修正しました

## v2.1.1
* SDK 修正
  * Client.Listener#onClosed() コールバック内で Camera2Capturer#stop() を呼び出すとクラッシュすることがある問題を修正しました

## v2.1.0
* API 変更
  * [connect メソッドの option.proxy.url で Web プロキシサーバの URL が指定できる](https://api.livestreaming.ricoh/docs/clientsdk-api-external-specification/#proxyoption)ようになりました。Client の通信が指定された Web プロキシサーバを通過するようになります
  * [libwebrtc のログをファイルに出力するメソッド setLibWebrtcLogOption を追加](https://api.livestreaming.ricoh/docs/clientsdk-api-external-specification/#setlibwebrtclogoption)しました
  * [Camera2VideoCapturer に getCameraCharacteristics と setCameraCustomParameters を追加](https://github.com/ricoh-live-streaming-api/android-sdk/tree/main/doc)しました
* SDK 修正
  * libwebrtc を m107 に更新しました
  * [NetworkError の追加と廃止](https://api.livestreaming.ricoh/docs/clientsdk-error-specification/#networkerror)を行いました
    * 追加: Room 管理 API (3 月までにリリース予定) でアプリケーションから切断された場合のエラーコード 53002 ConnectionClosedByApplication
    * 追加: Room の最大持続時間(24 時間)を越えて切断された場合のエラーコード 53003 MaxRoomPeriodExceeded
    * 廃止: 54000 OnTrackTimeout。同等の内容はログに出力されるのみになります
  * Android13 で H.264 の映像が送信できないことがある問題を修正しました
  * 自分が送信している映像コーデックと異なる映像コーデックで映像を送信している Connection が Room から退出するとクラッシュする場合がある問題を修正しました
  * UnityPixelReader でビデオフレームの width によって映像が乱れることがある問題を修正しました
  * UnityPlugin が 64bit 環境でクラッシュする問題を修正しました
* サンプルアプリ修正
  * option.proxy.url の使用例を追加しました
  * setLibWebrtcLogOption の使用例を追加しました
  * ThetaVideoEncoderFactory のコーデック指定を現在推奨の CodecUtils.getSupportedEncoderCodecInfo() を使うように変更しました
  * RoomType に sfu_large を追加しました

## v2.0.0
* API変更
  * 破壊的変更 Listenerが実装を要求するコールバックメソッドの引数を1つに統一
    * 変更に伴う[移行ガイド](./Guide.md)を追加
    * 今後コールバックが渡す情報が追加される場合、引数のオブジェクトの属性が増えることになる
  * onOpenのコールバック属性にAccessTokenのclaimのJSON文字列を追加
  * com.ricoh.livestreaming.unity.UnityPixelReaderを追加。UnityでVideoFrameからRGBデータを読み出すことができる
* SDK修正
  * libewebrtcを103に更新
  * videoのmuteTypeがhardmuteで指定された際、Roomの帯域幅割当消費の対象外になった
  * `Client#changeMediaRequirements`を実行時に指定したConnectionが同じタイミングで離脱するとエラーになる場合がある問題を修正
  * onOpenの直後やリスナー中でいくつかのメソッドを呼ぶと正常に動作しなかった問題を修正
  * onOpen発生前にdisconnectを呼び出すとエラーになる問題の修正
  * 特定のAndroid端末でdisconnectを呼び出すとアプリケーション全体を巻き込んでクラッシュすることがある問題の修正
  * SendingOptionのsending.video.maxBitrateKbpsの設定値をAndroidのエンコーダに設定するように修正
    * 設定値がない場合は以下の通り設定される
      * P2P、P2P/TURN => エンコーダの自動設定
      * SFU => maxBitrateKbpsのデフォルト値
  * 同じRoomに同じConnectionIDのConnectionが入室した際に、新しいConnectionが残り元のConnectionは退室する仕様変更に伴い、エラーコードを追加削除
    * 追加: 43603 SameConnectionIDJoined
    * 廃止: 43602 DuplicateConnectionIDOnConnect
  * 一部エラー内容の表現を改善
  * Clientが"open"の間ソケットのファイルディスクリプタが増加し続ける問題の修正
  * インフィニテグラライブラリを更新
  * 外付けカメラでUSB接続した直後の解像度から低い解像度へ変更した場合に外付けカメラから映像が出力されない不具合を修正
* サンプルアプリ修正
  * Listenerの変更対応
  * サンプルアプリ間接続の簡単のためroom_id, room_specを揃えた
* 共通修正
  * targetSdkVersionを31に更新

## v1.7.0
* API変更
  * THETA Xに対応
  * 送信映像フレームレートを送信中に変更できる`Client#changeVideoSendFramerate(int maxFramerate)`を追加
  * [Deprecated] `ThetaVideoEncoderFactory#setTargetBitrate(int targetBitrate)`の非推奨対応に伴う移行ガイドを追加 [移行ガイド参照](./Guide.md#v160 "移行ガイド")
  * (2022/06/29より本番環境で利用可能になりました)ConnectOptionのsending.enabledでクライアントの送信機能を無効にできるようにした。同一Room中に大量に送信機能が有効なクライアントが存在する場合、クライアントに大きな処理負荷や遅延が発生してしまうが、このオプションで低減することができる
* SDK修正
  * P2P、P2P/TURN時に`ThetaVideoEncoderFactory`のコンストラクタで渡されたサポートコーデック以外がコーデックとして選択されてしまう不具合を修正
  * P2P、P2P/TURN時の`Client#changeVideoSendBitrate(int maxBitrateKbps)`で指定したビットレートに制御できない不具合を修正
* サンプルアプリ修正
  * Connect時の`maxBitrateKbps`をbitrate選択スピナーの値の最大値で設定するように修正 (android-app)
  * THETAプラグインサンプルをTHETA Xでも動くように修正 (https://github.com/ricoh-live-streaming-api/android-sdk-samples/tree/main/android-device-samples/theta-plugin)

## v1.6.0
* API変更
  * changeVideoSendBitrateで接続中に映像送信ビットレートを変更できるようにした
  * (dev環境のみ提供のβ機能)ConnectOptionのsending.enabledでクライアントの送信機能を無効にできるようにした。同一Room中に大量に送信機能が有効なクライアントが存在する場合、クライアントに大きな処理負荷や遅延が発生してしまうが、このオプションで低減することができる
  * CodecUtils#getSupportedEncoderCodecInfoでシステムでサポートしているエンコーダのコーデックを取得できるようにした
  * CodecUtils#getSupportedDecoderCodecInfoでシステムでサポートしているデコーダのコーデックを取得できるようにした
  * [Deprecated] `ThetaVideoEncoderFactory#setTargetBitrate(int targetBitrate)`を非推奨に変更 [移行ガイド参照](./Guide.md#v160 "移行ガイド")
* SDK修正
  * Unity向けaarとAndroid Native向けaarを統合
  * 規定時間内にIceConnectionが接続確立しなかった場合にIceConnectionTimeoutエラーを通知
  * closing/closed状態で発生するInternalErrorをListenerへ通知しないように修正
  * WebSocket切断検知のためにWebSocketコネクションタイムアウト45秒を設定
* サンプルアプリ修正
  * 以下のサンプルアプリを別リポジトリに移行
    * theta-plugin
    * wearable-glass
    * unity-app
* 共通修正
  * minSdkVersionを25から23に変更

## v1.6.0-alpha1
* SDK修正
  * P2P、P2P/TURN時に`ThetaVideoEncoderFactory`のコンストラクタで渡されたサポートコーデック以外がコーデックとして選択されてしまう不具合を修正

## v1.5.0
* SDK修正
  * libWebRTCをM96に変更
  * Unity向け修正
    * マルチスレッドレンダリングに対応可能にするために `getRenderEventFunc()` を追加
      * 詳細な利用方法はUnityMultithreadRendering.mdを参照
* 共通修正
  * compileSdkVersionを30から31に変更

## v1.4.0
* API変更
  * changeMediaRequirementsで対向connectionごとにvideoを受信するか指定できるようにした
  * OptionのiceServersProtocolでTURNサーバに接続する際にどのトランスポートプロトコルで接続するか指定できるようにした。この属性で"tls"を指定してTCP443ポートの強制が可能になり、他のトランスポートプロトコルを使ってパフォーマンス最適化する余地を犠牲にして、ファイヤーウォールやプロキシを通過する確率を上げることができる
* SDK修正
  * 特定のタイミングでchangeMute、updateTrackMetaを実行した場合に対向connectionに内容が通知されない不具合を修正
  * 要求されたRoomSpecに対応するSFUまたはTURNが一時的にクラウド上に存在しない場合に専用のエラーコード53806を追加
  * 初期化処理時にハードウェアでサポートしているコーデック一覧をログ出力するように修正
  * VP8/VP9指定時でハードウェアコーデックがない場合にソフトウェアコーデックを使用するように修正
  * open状態以外で処理できないイベントハンドラでInternalErrorにならないように修正
* サンプルアプリ修正
  * android-app
    * ScreenShareActivityを追加
  * theta-plugin
    * オーディオミュート機能を追加
    * jcenterからmavenCentralへの移行のためにTHETA Web Apiを呼び出すように修正してtheta4jライブラリ依存を解消
  * wearable-glass
    * オーディオミュート機能を追加
* 共通修正
  * gradle ビルドスクリプトで指定するリモートリポジトリを jcenter() から mavenCentral() に変更

## v1.3.0
* compileSdkVersionとtargetSdkVersionを`29`から`30`に変更
* SDK修正
  * open状態ではあるが接続が完了する前の僅かな間に`Client#changeMute()`を呼ぶとエラーが発生する問題を修正
    * これに伴い以下のエラーコードを修正
    * エラーコードのみの修正でエラーメッセージに変更はありません
      * 旧`45207` => 新`45605`
      * 旧`45216` => 新`45614`
  * THETA利用時に設定しているShootingModeとStitchingModeがログにdebugで出力されるように修正
  * P2P、P2P_TURN接続時でも`Client#updateMeta()`が実行できるように修正
    * これに伴い以下のエラーコードを削除
      * `45003`
  * 外付けカメラ配信で画像の右端が崩れてしまう問題を修正
* サンプルアプリ修正
  * 共通
    * gradleバージョンを`3.5.2`から`4.2.0`に更新
    * kotlin-gradle-pluginバージョンを`1.3.41`から`1.4.32`に更新
    * fat-aarバージョンを`1.2.12`から`1.3.6`に更新
    * 利用している外部ライブラリのアップデート
    * `kotlin-android-extensions`をViewBindingに変更
  * `wearable-glass`
    * READMEドキュメントの誤記を修正

## v1.2.0
* 正式公開開始