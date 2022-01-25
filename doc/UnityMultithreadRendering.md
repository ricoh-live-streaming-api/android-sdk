# Unityアプリでマルチスレッドレンダリングに対応する

* マルチスレッドレンダリングに対応するためにはUnityのライフサイクルのStart()でGL.IssuePluginEvent(getRenderEventFunc(), 0)を呼ぶ必要があります。
  * getRenderEventFunc()を使用するにはDllImportでextern宣言が必要です。

* 実装例
```cs
    [DllImport("ls-client")]
    private static extern IntPtr getRenderEventFunc();
…
    IEnumerator Start() {
        GL.IssuePluginEvent(getRenderEventFunc(), 0);
```