# 移行ガイド

## v2.0.0
* v2.0.0 より `Client#Listener` で定義している各種イベントハンドラの引数が変更になりましたので、下記の通り移行をお願いします
  * `Listener#onConnecting()`
    * 旧
      ```kotlin
      override fun onConnecting() {}
      ```
    * 新
      ```kotlin
      override fun onConnecting(event: LSConnectingEvent) {}
      ```
  * `Listener#onOpen()`
    * 旧
      ```kotlin
      override fun onOpen() {}
      ```
    * 新
      ```kotlin
      override fun onOpen(event: LSOpenEvent) {}
      ```
  * `Listener#onClosing()`
    * 旧
      ```kotlin
      override fun onClosing() {}
      ```
    * 新
      ```kotlin
      override fun onClosing(event: LSClosingEvent) {}
      ```
  * `Listener#onClosed()`
    * 旧
      ```kotlin
      override fun onClosed() {}
      ```
    * 新
      ```kotlin
      override fun onClosed(event: LSClosedEvent) {}
      ```
  * `Listener#onAddLocalTrack()`
    * 旧
      ```kotlin
      override fun onAddLocalTrack(mediaStreamtrack: MediaStreamTrack, mediaStream: MediaStream) {
          val track: MediaStreamTrack = mediaStreamTrack
          val stream: MediaStream = mediaStream
      }
      ```
    * 新
      ```kotlin
      override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
          val track: MediaStreamTrack = event.mediaStreamTrack
          val stream: MediaStream = event.stream
      }
      ```
  * `Listener#onAddRemoteConnection()`
    * 旧
      ```kotlin
      override fun onAddRemoteConnection(connectionId: String, meta: Map<String, Any>) {
          val connId: String = connectionId
          val metadata: Map<String, Any> = meta
      }
      ```
    * 新
      ```kotlin
      override fun onAddRemoteConnection(event: LSAddRemoteConnectionEvent) {
          val connId: String = event.connectionId
          val metadata: Map<String, Any> = event.meta
      }
      ```
  * `Listener#onRemoveRemoteConnection()`
    * 旧
      ```kotlin
      override fun onRemoveRemoteConnection(connectionId: String, meta: Map<String, Any>, mediaStreamTracks: List<MediaStreamTrack>) {
          val connId: String = connectionId
          val metadata: Map<String, Any> = meta
          val tracks: List<MediaStreamTrack> = mediaStreamTracks
      }
      ```
    * 新
      ```kotlin
      override fun onRemoveRemoteConnection(event: LSRemoveRemoteConnectionEvent) {
          val connId: String = event.connectionId
          val metadata: Map<String, Any> = event.meta
          val tracks: List<MediaStreamTrack> = event.mediaStreamTracks
      }
      ```
  * `Listener#onAddRemoteTrack()`
    * 旧
      ```kotlin
      override fun onAddRemoteTrack(connectionId: String, mediaStream: MediaStream, mediaStreamTrack: MediaStreamTrack, meta: Map<String, Any>, mute: MuteType) {
          val connId: String = connectionId
          val stream: MediaStream = mediaStream
          val track: MediaStreamTrack = mediaStreamTrack
          val metadata: Map<String, Any> = meta
          val muteType: MuteType = mute
      }
      ```
    * 新
      ```kotlin
      override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
          val connId: String = event.connectionId
          val stream: MediaStream = event.stream
          val track: MediaStreamTrack = event.mediaStreamTrack
          val metadata: Map<String, Any> = event.meta
          val muteType: MuteType = event.mute
      }
      ```
  * `Listener#onUpdateRemoteConnection()`
    * 旧
      ```kotlin
      override fun onUpdateRemoteConnection(connectionId: String, meta: Map<String, Any>) {
          val connId: String = connectionId
          val metadata: Map<String, Any> = meta
      }
      ```
    * 新
      ```kotlin
      override fun onUpdateRemoteConnection(event: LSUpdateRemoteConnectionEvent) {
          val connId: String = event.connectionId
          val metadata: Map<String, Any> = event.meta
      }
      ```
  * `Listener#onUpdateRemoteTrack()`
    * 旧
      ```kotlin
      override fun onUpdateRemoteTrack(connectionId: String, mediaStream: MediaStream, mediaStreamTrack: MediaStreamTrack, meta: Map<String, Any>) {
          val connId: String = connectionId
          val stream: MediaStream = mediaStream
          val track: MediaStreamTrack = mediaStreamTrack
          val metadata: Map<String, Any> = meta
      }
      ```
    * 新
      ```kotlin
      override fun onUpdateRemoteTrack(event: LSUpdateRemoteTrackEvent) {
          val connId: String = event.connectionId
          val stream: MediaStream = event.stream
          val track: MediaStreamTrack = event.mediaStreamTrack
          val metadata: Map<String, Any> = event.meta
      }
      ```
  * `Listener#onUpdateMute()`
    * 旧
      ```kotlin
      override fun onUpdateMute(connectionId: String, mediaStream: MediaStream, mediaStreamTrack: MediaStreamTrack, mute: MuteType) {
          val connId: String = connectionId
          val stream: MediaStream = mediaStream
          val track: MediaStreamTrack = mediaStreamTrack
          val muteType: MuteType = mute
      }
      ```
    * 新
      ```kotlin
      override fun onUpdateMute(event: LSUpdateMuteEvent) {
          val connId: String = event.connectionId
          val stream: MediaStream = event.stream
          val track: MediaStreamTrack = event.mediaStreamTrack
          val muteType: MuteType = event.mute
      }
      ```
  * `Listener#onChangeStability()`
    * 旧
      ```kotlin
      override fun onChangeStability(connectionId: String, stability: Stability) {
          val connId: String = connectionId
          val state: Stability = stability
      }
      ```
    * 新
      ```kotlin
      override fun onChangeStability(event: LSChangeStabilityEvent) {
          val connId: String = event.connectionId
          val state: Stability = event.stability
      }
      ```

## v1.6.0
* `ThetaVideoEncoderFactory#setTargetBitrate(int targetBitrate)`を非推奨に変更
  * 利用されている方は`Client#changeVideoSendBitrate(int maxBitrateKbps)`に変更をお願いします
  * `Client#changeVideoSendBitrate(int maxBitrateKbps)`は`Client#getState()`が`OPEN`の状態で使用可能となります
    * `OPEN`以外の状態で実行しようとした場合はエラー(45015)となります
  * また、引数へ渡すbitrateの単位がbpsからkbpsに変更となりますので、移行の際は`従来の引数bitrate / 1024`の対応をお願いします
  * `Client#changeVideoSendBitrate(int maxBitrateKbps)`で100kbps未満の値を設定することはできません
    * `ThetaVideoEncoderFactory#setTargetBitrate(int targetBitrate)`における0(Auto)設定相当にあたる値はありませんので、利用するbitrate値による設定をお願いします
