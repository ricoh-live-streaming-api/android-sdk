# CHANGELOG

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
* API修正
  * `UnityPlugin#setUnityContext()`を追加
    * APIを使用する際は以下が必要です
      * `UnityPlugin#setUnityContext()`はUnityのライフサイクルのStart()で呼ぶ
      * UnityのPlayer Settingsの `Multithreaded Rendering` を OFFに設定する
* SDK修正
  * HardMute⇔SoftMuteの切り替えを可能にするよう修正
  * Trackメタデータを設定していない状態で`Client#changeMute()`を呼ぶとエラーが発生する問題を修正
  * `libWebRTC`を`M88`に変更
* サンプルアプリ修正
  * `theta-plugin`
    * `local.properties` に `video_bitrate` が指定されていない場合はビデオの送信ビットレート上限値を10Mbpsにするよう修正
    * Room帯域幅予約値を 25 に変更
    * 変更できる送信ビットレートの一覧を修正
    * 変更できるキャプチャフォーマットの一覧を修正
    * READMEドキュメントを修正
      * モードボタン短押しで切り替わる解像度・スティッチングモードが実際の動作と異なっていたので修正
      * Room帯域幅予約値を追加
      * RICOH Live Streaming Conferenceと組み合わせでの動作を追加
  * `wearable-glass`
    * 4Kの送信フレームレートを 15fps に変更
    * Room帯域幅予約値を 25 に変更
    * Connectionメタデータに設定する値を修正
    * Trackメタデータに設定する値を修正
    * READMEドキュメントを修正
      * Room帯域幅予約値を追加
      * RICOH Live Streaming Conferenceと組み合わせでの動作を追加
  * setting_app修正
    * ビデオの送信ビットレートの上限値のデフォルト値を7Mbpsに変更
    * 送信解像度のデフォルト値を4Kに変更
* 表記ゆれの統一
  * `LiveStreaming`、`Live Streaming`を`RICOH Live Streaming`に修正
  * `Ricoh`を`RICOH`に修正
* M400向けサンプル(`wearable-glass`、`setting-app`)をリリース物に追加
* ソフトウェア使用許諾契約書を追加
* licensesフォルダ以下の使用OSSライセンスを更新

## v1.1.0
* SDK修正
  * THETA Vで`ThetaCameraCapturer#takePicture()`が失敗する問題の修正

## v1.0.1
* SDK修正
  * WebSocketでエラーが発生した場合にWebSocketErrorを通知するよう修正
  * Signalingエラーで正常終了時にも関わらずInternalErrorを通知していた問題を修正
* サンプルアプリ修正
  * bitrate_reservation_mbps の設定例を記載

## v1.0.0
* API 変更
  * `Client#onError(SDKException)` を `Client#onError(SDKErrorEvent)` に変更
  * `SDKException` を `SDKError` に変更
* SDK修正
  * `Client#connect()` でOptionにLocalLSTrackが設定されていない場合はエラーとするよう修正
  * P2P接続時に `Client#updateMeta()` が呼ばれた場合エラーとするよう修正
  * SignalingのURLを変更
  * エラーコード修正
* サンプルアプリ修正
  * READMEドキュメントにログ出力の記載を追加
  
## v0.5.1
* SDK修正
  * Client#onChangeStability()の通知タイミングを修正
    * PeerConnectionState.DISCONNECTED を受信したら即時通知するよう修正
  * Hardmuteの内部処理を修正
    * Hardmute時にもmediaStreamTrack.setEnabled(false)を呼ぶよう修正

## v0.5.0
* サンプルアプリ修正
  * JwtAccessTokenの有効期限を1時間に変更
  * theta-pluginのローカル時間をWebAPI経由で取得するよう修正
    * build.gradle の dependencies に `implementation("org.theta4j:theta-web-api:1.4.0")` の追加が必要

* API 変更
  * `Track` を `LSTrack` に変更
  * `TrackOption` を `LSTrackOption` に変更
  * `Option#getLocalTracks()` を `Option#getLocalLSTracks()` に変更
  * `Option.Builder#localTracks(List<Track>)` を `Option.Builder#localLSTracks(List<LSTrack>)` に変更
  * DMCに対応
    * `SendingVideoOption`、`SendingOption`、 `ReceivingOption` を追加
    * `Video` を削除
      * `Codec`、 `Bitrate` の設定は `SendingVideoOption` で行うよう変更
    * `Audio` を削除
    * `Role` を削除
      * `Role` の設定は `ReceivingOption` で行うよう変更

`Codec`、 `Bitrate`設定の修正前の例:

```kotlin
val option = Option.Builder()
        .video(Video(Video.Codec.H264, BuildConfig.VIDEO_BITRATE))
        .audio(audioOption)
```

`Codec`、 `Bitrate`設定の修正後の例:

```kotlin
val option = Option.Builder()
        .sending(SendingOption(
                SendingVideoOption.Builder()
                        .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                        .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                        .maxBitrateKbps(BuildConfig.VIDEO_BITRATE)
                        .build()))
```

`Role`設定の修正前の例:

```kotlin
val option = Option.Builder()
                .role(Role.SEND_ONLY)
```

`Role`設定の修正後の例:

```kotlin
val option = Option.Builder()
                .receiving(ReceivingOption(false))
```

## v0.4.2
* SDK修正
  * `libWebRTC`を`M85`に変更

## v0.4.1
* API 変更
  * `ThetaCameraCapturer#getCameraParameters()` を追加

## v0.4.0
* API 変更
  * `Client#onRemoveRemoteConnection()` のパラメータにList<MediaStreamTrack>を追加
  　　* `Client.Listener#onRemoveRemoteConnection(String, Map<String, Object>)` を `Client.Listener#onRemoveRemoteConnection(String, Map<String, Object>, List<MediaStreamTrack>)` に変更
* SDK修正
  * SignalingのURLを変更
  * Signalingエラーの通知を修正
* サンプルアプリ修正
  * JwtAccessTokenに設定するnbf、expの値を修正

## v0.3.0
* API 変更
  * 外付けUSBカメラでMJPEGフォーマットに対応
  * 外付けUSBカメラのイベントリスナーを追加
    * `UvcVideoCapturer.Listener#onDeviceOpened(List<Format> formats)` を追加
    * `UvcVideoCapturer.Listener#onDeviceDetached()` を追加
    * `UvcVideoCapturer#setEventListener(Listener listener)` を追加
  * ミュート機能に対応
    * `Client#changeMute(Track targetTrack, MuteType nextMuteType)` を追加
  * `Client.Listener` を変更
    * `Client.Listener#onAddRemoteTrack(String connectionId, MediaStream stream MediaStreamTrack track)` を `onAddRemoteTrack(String connectionId, MediaStream stream MediaStreamTrack track, MuteType muteType)` に変更
    * `Client.Listener#onUpdateMute(String connectionId, MediaStream stream, MediaStreamTrack track, MuteType muteType)` を追加
    * `Client.Listener#onChangeStability(String connectionId, Stability stability)` を追加
  * ConnectのOptionに `Role` と `IceTransportPolicy` を追加
    * `Option#role(Role role)` を追加
    * `Option#IceTransportPolicy(IceTransportPolicy iceTransportPolicy)` を追加
  * `TrackOption` クラスを追加
  * `Track` のコンストラクタを変更
    * `Track(MediaStreamTrack track, MediaStream stream, Map<String, Object> meta)` を `Track(MediaStreamTrack track, MediaStream stream, TrackOption option)` に変更
  * `ThetaCameraCapturer#setCameraParameters(android.hardware.Camera.Parameters parameters)` を追加
  * `ThetaCameraCapturer#setCameraCustomParameters(HashMap<String, Object> customParameters)` を追加
* サンプルアプリ変更
  * RoomSpecからUseTurnを削除
  * RoomSpecのRoomTypeにP2P_TURNを追加
  * `android-app`のBidirActivityでデバイスのミュートに対応
  * `android-app`のBidirActivity、UvcCameraActivityで多拠点表示に対応
  * `android-app`のUvcCameraActivityで配信で使用するカメラのCapabilityを選択できるよう修正

## v0.2.0
* API 変更
    * `Client#replaceMediaStreamTrack`を追加
      * 配信途中でのデバイス切り替えが可能になります
    * Unity向けAPIの変更
      * `UnityPlugin#getYPlane`を`UnityPlugin#copyYPlaneData`に変更
      * `UnityPlugin#getUPlane`を`UnityPlugin#copyUPlaneData`に変更
      * `UnityPlugin#getVPlane`を`UnityPlugin#copyVPlaneData`に変更
* SDK修正
    * SFU時のPeerConnectionのconfigにIceTransportsType.RELAYを追加
* サンプルアプリ変更
    * RoomSpecのUseTurnをtrueに変更
    * `unity_app`のAndroid10対応
    * `android-app`のBidirActivityで配信途中でのデバイス切り替えに対応

## v0.1.0
* API 変更
    * パッケージ名を`com.ricoh.livestreaming.rdc`から`com.ricoh.livestreaming`に変更
    * `Camera2VideoCapturer`、`ThetaCameraCapturer`をアプリからSDKに移動
    * `Client#getStats()`を追加
    * `Client#connect()`で指定するOptionにVideoおよびAudioのコーデック設定、ビットレート設定ができるよう修正
    * 外付けUSBカメラに対応
      * 対応中のため一部条件でしか動作しません
    * `Client.Listener#onError(String messageType, String errorCode)`を`Client.Listener#onError(SDKException error)`
      * `SDKException#toReportString()`で詳細なエラー情報が取得できます
* サンプルアプリ変更
    * アプリ名変更
      * `rdc-android-app`を`android-app`に変更
      * `rdc-theta-plugin`を`theta-plugin`に変更
      * `rdc-unity-app`を`unity-app`に変更
    * `android-app`に`UvcCameraActivity`を追加
      * 対応中のため一部条件でしか動作しません
    * `theta-plugin`アプリのConnectメタデータとTrackメタデータを修正

## v0.0.16
* 新APIに対応

## v0.0.15
* API 変更
    * エンコーダのサポートコーデックにVP8とVP9を追加
    * libWebRTC 80.8.0に対応
    * `loggingSeverity`オプションを追加
        * libWebRTCのログ出力レベルの設定
        * デフォルトはINFO
* サンプルアプリ変更
    * rdc-wearable-glass、rdc-setting-appで送信ビットレートが設定できるよう修正
    * rdc-wearable-glassで切断時にANRが発生する問題を修正
    * rdc-android-app、rdc-theta-pluginでRoomID、ログレベルがアプリで設定できるよう修正
    * Bidirアプリでカメラ選択ができるよう修正
    * rdc-theta-pluginで配信前にシャッターボタン短押し、モードボタン短押しをするとクラッシュする問題の修正
    * Unityサンプルアプリを追加
* その他
    * cappella.aarにlibwebrtc.aarを含めるよう変更

## v0.0.14

* API 変更
    * fps設定が反映されない不具合を修正
    * libWebRTC M79 の変更を取り込み

## v0.0.13

* API 変更
    *  旧THETA Firmware向けに実装していたworkaroundを削除
    *  fps改善
    *  Unity対応
    *  Iフレームの強制送出間隔を300秒に変更
    *  libWebRTC 79.5.0に対応
    *  API追加（★利用する場合各アプリコードでの対応が必要）
        * `getCameraParameters` : CameraParametersを取得して返す。Camera動作中しか値が取れない。それ以外のタイミングだとexceptionになることがある。
        * `setCameraParameters` : CameraParametersをセットする。Camera動作中しか値が取れない。それ以外のタイミングだとexceptionになることがある。stop / updateCameraFormat / setCameraCustomParameters を呼びだしたタイミングで設定値は消滅する。SDK内部ではstop/startしないため設定されるパラメータによっては即座には反映されない事がある。
        * `setCameraCustomParameters` : (stopしても消えない)カスタム用のCameraParametersをセットできる。SDK内部で自動的にstop/startされる。Shooting Mode / Stitchingについては既存の updateCaptureFormat に渡された設定値の方が優先される。
* サンプルアプリ変更
    *  rdc-theta-pluginのモード設定を追加
    *  ウェアラブルグラス(M400)向けのサンプルアプリと設定アプリを追加
    *  rdc-android-appの全てのactivityでRoomIDのsuffixを指定できるようにした
    *  rdc-theta-pluginの接続をmultistream=trueにした
    *  statsloggerのfilter設定をlibWebRTC 79.5.0に合わせて修正（★各アプリでの対応が必要）

## v0.0.12

* API変更
    * `enableCpuOveruseDetection`オプションを追加
        * CPU負荷による解像度調整等を行うかどうかの設定
        * デフォルトはfalse(CPU負荷による調整は行わない）
    * 特定のTHETA Firm Versionの不具合のworkaroundを削除
    * 特定のスマートホン機種でカメラ映像の乱れが発生する事があった問題を修正
        * 【制約】改善はしたが、一部機種(Pixel3等)においてはまだ乱れが発生する
    * libWebRTCのデバッグログをinfoで出力するようにした
* サンプルアプリ修正
    * statsログにタイムスタンプを追加
    * statsログのフィルタに`type=media-source`を出力するように追加
    * BiDirアプリ
        * RoomIDのSuffixをテキストボックスで指定できるようにした
        * 4KとFHDを選択できるようにした
            * 接続時の設定が適用される
            * 接続中の変更は不可

## v0.0.11

* API 変更
    * `RdcClient.Listener#onAddRemoteVideoTrack(VideoTrack)` を `#onAddRemoteTrack(MediaStream, MediaStreamTrack)` に変更
    * `RdcClient.Listener#onRemoveRemoteTrack(String, String)` を追加
    * `VideoCapturer` の指定を省略できる `RdcClient#connect` メソッドを追加
* サンプルアプリ変更
    * rdc-android-app に `RecvActivity` を追加
        * 映像受信専用のサンプル (音声は双方向)
    * `Camera1VideoCapturer` を削除
* その他
    * 接続後のストリーム増減に対応

### API 変更への対応例

修正前の例:

```kotlin
override fun onAddRemoteVideoTrack(track: VideoTrack) {
    LOGGER.debug("RdcClient#onAddRemoteVideoTrack({})", track.id())
    track.addSink(remote_view)
}
```

修正後の例:

```kotlin

override fun onAddRemoteTrack(stream: MediaStream, track: MediaStreamTrack) {
    LOGGER.debug("RdcClient#onAddRemoteTrack({}, {})", stream.id, track.id())

    if (track is VideoTrack) {
        track.addSink(remote_view) // Smart Cast で VideoTrack のメソッドを参照できるようになる
    }
}
```

## v0.0.10

* API 変更
    * タイポ修正 : `Audio.PMCU` → `Audio.PCMU`
    * `RdcClient` の変更 (詳細は API 変更への対応例を参照)
        * `connect` メソッドの呼び出しで認証・認可、チケット作成、接続の3ステップの処理を実行していたが、これを個別のメソッドに分解
        * コンストラクタでイベントリスナーを登録していたが `setEventListener` メソッドで設定するように変更
    * パラメータ名 `channelID` を `roomID` に統一 (扱いはこれまでと変わらない)
* サンプルアプリ変更
    * rdc-bidir と rdc-file-sender を rdc-android-app として統合
        * BidirActivity が rdc-bidir に相当
        * FileSenderActivity が rdc-file-sender に相当
        * Runtime Permission のダイアログ表示を実装
    * rdc-theta-sender
        * rdc-theta-plugin に改名
        * パッケージを `com.ricoh.livestreaming.rdc.theta` に変更
        * 公式の `theta-plugin-library` を利用
    * 共通
        * RTCStatsReport の出力先を sdcard 直下から、アプリのデータディレクトリ下に変更
* その他
    * `ThetaCapturer#takePicture` のスティッチ方式を動的スティッチに変更

### API 変更への対応例

`RdcClient` のイベントリスナーの

修正前の例:

```kotlin
// インスタンス生成 + 認証情報設定 + リスナー登録
val client = RdcClient(context, eglContext, clientID, clientSecret, listener)

// 接続設定作成
val video = Video(Video.Codec.H264, bitrate)
val audio = Audio(Audio.Codec.OPUS)

// 認証・認可 + チケット取得 + 接続
client.connect(channelID, video, audio, capturer)
```

修正後の例 (プロダクションサーバーに接続する場合):

```kotlin
// インスタンス生成
val client = RdcClient(context, eglContext)

// リスナー登録
client.setEventListener(listener)

// 認証・認可
val accessToken = AuthClient().clientCredentialGrant(clientID, clientSecret)

// チケット取得
val ticket = RoomClient().createTicket(accessToken, roomID, Direction.UP)

// 接続設定作成
val config = Configuration.Builder()
        .video(Video(Video.Codec.H264, bitrate))
        .audio(Audio(Audio.Codec.OPUS))
        .multistream(true)
        .build()

// 接続
client.connect(ticket, config, capturer)
```

修正後の例 (開発サーバーに接続する例): 

```kotlin
// インスタンス生成
val client = RdcClient(context, eglContext)

// リスナー登録
client.setEventListener(listener)

// 認証・認可は不要

// チケット取得
val ticket = RoomClient("開発サーバーのURL").createTicket("開発サーバーのアクセストークン", roomID, Direction.UP)

// 接続設定作成は同じ
val config = ...

// 接続
client.connect(ticket, config, capturer)
```

## v0.0.9

* API 変更
    * `ThetaCapturer`
        * `takePicture` メソッドを追加
* サンプル
    * rdc-bidir
        * Camera2 API ベースに変更
    * rdc-theta-sender
        * 静止画撮影機能を追加 (配信中にシャッターボタンを短く押す)
    * RTCStatsLogger の修正
        * デフォルトで出力する type に `outbound-rtp` と `sender` を追加
        * 出力結果に `type` 属性を追加
    * Internal Tracing Capture に関するコメントを修正
        * onConnecting イベントから onConnected イベントに移動

## v0.0.8

マルチストリームに対応、送受信映像の描画に対応

* API 変更
    * `RdcClient.Listener#onAddLocalVideoTrack(VideoTrack)` を追加
    * `RdcClient.Listener#onAddRemoteVideoTrack(VideoTrack)` を追加
* サンプル
    * 接続に失敗した場合に `NullPointerException` が発生する問題を修正
    * Androidスマートフォン向けの双方向ビデオチャットアプリを追加
        * 音声・映像の双方向通信
        * 送信映像・受信映像の画面への描画機能
    * RDC File Sender に映像のローカルプレビューを追加
* バグ修正
    * 接続に失敗した場合に `RdcClient.Listener#onDisconnected` が呼ばれない問題を修正

### API 変更への対応

`RdcClient#Listener` に映像トラック追加イベントが追加されたため、実装する必要がある。

以下のように実装すると、送受信映像を画面に描画できる。

```java
@Override
void onAddLocalVideoTrack(VideoTrack track) {
    // localView は Activity 上に配置した org.webrtc.SurfaceViewRenderer
    track.addSink(localView)
}

@Override
void onAddLocalVideoTrack(VideoTrack track) {
    // remoteView は Activity 上に配置した org.webrtc.SurfaceViewRenderer
    track.addSink(remoteView)
}
```

イベントを利用しない場合は空の実装で問題ない。

## v0.0.7

* API 変更
    * `RdcClient`
        * コンストラクタの第2引数に `org.webrtc.EglBase14.Context` を追加 (詳細な利用方法はサンプルを参照)
        * `setVideoEncoderFactory(com.webrtc.VideoEncoderFactory)` メソッドを追加
    * `ThetaVideoEncoderFactory` クラスを追加
        * このクラスのインスタンスを生成して `RdcClient#setVideoEncoderFactory` メソッドで設定する。
        * `setTargetBitrate(int)` メソッドで目標ビットレート(kbps)を設定できる。(0を指定すると自動)
* サンプル
    * RDC THETA Sender
        * ビットレートの動的指定に対応 (無線ボタンで切り替え)
* その他
    * https://git.ucs.ricoh.co.jp/repo/maven に登録開始

### API 変更への対応

v0.0.7 より、`org.webrtc.EglBase14.Context` をアプリ側で管理する必要がある。

Java で EglBase を生成・開放する例を示す。

```java
private EglBase egl;

@Override
void onCreate(Bundle savedInstanceState) {
    ...
    egl = EglBase.create();
    eglContext = (EglBase14.Context) egl.eglBaseContext;
    ...
}

@Override
void onDestroy() {
    ...
    if (egl != null) {
        egl.release();
        egl = null;
    }
    ...
}
```

## v0.0.6

* API 変更
    * `ThetaCapturer` が動的なキャプチャフォーマット変更とFPS の指定に対応
        * `ThetaCapturer#updateCaptureFormat` メソッドを呼ぶ。接続中も呼び出し可能で、接続を維持したまま変更が反映される。
    * `CaptureFormat` クラスを追加 (ShootingMode, StitchingMode, FPS をまとめたクラス)
* サンプル
    * RDC THETA Sender
        * キャプチャフォーマットの動的な変更に対応 (モードボタンで切り替え)
* その他
    * libwebrtc を M75 にアップデート
    * FPS の向上 (`Camera#setPreviewSize` の設定)

### API 変更への対応

* `ThetaCapturer` のコンストラクタが変更されている。
    * `ShootingMode` と `StitchingMode` を個別に指定するのではなく `CaptureFormat` クラスのインスタンスを作って指定する。

## v0.0.5

* API 変更
    * 動画ファイルを配信する `CompressedVideoFileCapturer` を追加
    * `ThetaCapturer` にスティッチングモードの指定機能を追加
    * `ShootingMode` クラスが `com.ricoh.livestreaming.theta` パッケージ直下に移動
* サンプル
    * RDC File Sender
        * `CompressedVideoFileCapturer` を利用して動画ファイルを配信するアプリを追加
    * RDC THETA Sender
        * アプリ名を変更 `Cappella Example` -> `RDC THETA Sender`
        * パッケージ名変更 `ricoh.cappella.example` -> `com.ricoh.livestreaming.rdc.example`
        * 接続完了時、切断完了時の効果音を追加
        * 配信音声を 4ch からモノラルに変更
        * スティッチングのデフォルトを Dualfish-eye に変更
        * 設定ファイル
            * ファイル名を `gradle.properties` から `local.properties` に変更
            * ビットレート設定を追加
* バグ修正
    * ログ文字列中の変数が正常に出力されない問題を修正
    * RDC THETA Sender がスリープによる切断直後の再接続に失敗する問題を修正
* その他
    * libwebrtc を M73 にアップデート
    * サポートライブラリを AndroidX に変更

### API 変更への対応方法

* `com.ricoh.livestreaming.theta.ThetaCapturer.ShootingMode` を `com.ricoh.livestreaming.theta.ShootingMode` に書き換える。
* `com.ricoh.livestreaming.theta.ThetaCapturer` のコンストラクタのシグニチャ変更対応
    * 第2引数として `StitchingMode` を指定する。
    * `StitchingMode#RIC_STATIC_STITCHING` を指定すると、これまでと同様の Equirectangular 形式となる。

### サンプルアプリを動かす場合の注意点

* `gradle.properties` に記載していた Client ID 等の情報は `local.properties` に移動させる。
    * `gradle.properties` は `.gitignore` から削除したため、今後は `gradle.properties` に秘密情報は載せない。
    * `local.properties` にビットレート設定を追加する。例えば 5Mbps であれば以下のように書く。
    ```
    bitrate=50000
    ```
* `gradle.properties` に以下の設定を記載する。(AndroidX 対応)
    ```properties
    android.useAndroidX=true
    android.enableJetifier=true
    ```

## v0.0.4

* 機能追加
    * SLF4J によるログ設定
* API 変更
    * パッケージ名変更
        * `ricoh.cappella.*` -> `com.ricoh.livestreaming.*`
        * `ricoh.cappella.ThetaCapturer` -> `com.ricoh.livestreaming.theta.ThetaCapturer`
* サンプル
    * ログ出力を `anroid.util.Log` から SLF4J + Logbackに切り替え

## v0.0.3

* 機能追加
    * [RTCStatsReport](https://developer.mozilla.org/en-US/docs/Web/API/RTCStatsReport) の通知イベントを追加 (1秒周期で通知)
* API 変更
    * `RdcClient.Listener` インタフェースに `onRTCStatsReport` メソッドを追加
* サンプル
    * RTCStatsReport をログファイルに書き込む機能を追加

## v0.0.2

* 機能追加
    * 開発用サーバーへの接続に対応
* API 変更
    * `RdcClient` のコンストラクタを廃止し `RdcClient#create` メソッドを導入 (引数と仕様は同じ)
    * `RdcClient#createForDevelopment` メソッドを導入 (開発向け機能のため `@Deprecated` にしている)
* サンプル
    * 開発用サーバーへの接続に対応 (設定ファイルで接続先を切り替え)
    * 設定ファイルに項目追加 (詳細は `rdc-theta-sender/README.md` に記載)

## v0.0.1

* 初版
