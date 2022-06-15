# 移行ガイド

## v1.6.0
* `ThetaVideoEncoderFactory#setTargetBitrate(int targetBitrate)`を非推奨に変更
  * 利用されている方は`Client#changeVideoSendBitrate(int maxBitrateKbps)`に変更をお願いします
  * `Client#changeVideoSendBitrate(int maxBitrateKbps)`は`Client#getState()`が`OPEN`の状態で使用可能となります
    * `OPEN`以外の状態で実行しようとした場合はエラー(45015)となります
  * また、引数へ渡すbitrateの単位がbpsからkbpsに変更となりますので、移行の際は`従来の引数bitrate / 1024`の対応をお願いします
  * `Client#changeVideoSendBitrate(int maxBitrateKbps)`で100kbps未満の値を設定することはできません
    * `ThetaVideoEncoderFactory#setTargetBitrate(int targetBitrate)`における0(Auto)設定相当にあたる値はありませんので、利用するbitrate値による設定をお願いします
