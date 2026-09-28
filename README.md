**日本語** | [English](README.en.md)

# APAudio for Windows

**APAudio** is a completely free AirPlay 1 / RAOP audio receiver for Windows.

iPhoneやMacのAirPlay音声出力先としてWindows PCを使い、PCのスピーカーや接続中のオーディオ機器から音声を再生するための個人開発アプリです。

> Windows版のみ公開しています。Android版は公開していません。  
> APAudio is an independent project and is not affiliated with or endorsed by Apple Inc.

## 主な機能

- AirPlay 1 / RAOP の音声受信
- Apple Lossless (ALAC) のデコードとWindowsでのPCM再生
- 曲名・アーティスト・アルバム・アートワーク・再生位置の表示（送信元が提供する場合）
- 送信元の音量を反映
- System Tray常駐
- ウィンドウを閉じても受信を継続し、トレイから終了
- Javaランタイム同梱版を配布予定
- アカウント登録、サブスクリプション、広告なし

## ダウンロード

公開後はGitHub Releasesから以下を配布します。

- **EXE installer** — 通常はこちら
- **Portable ZIP** — インストールせず試したい場合

現時点のパッケージバージョンは **0.3.0** です。

### Windowsの警告について

現時点ではコード署名証明書を使用していないため、EXE起動時にWindows SmartScreenの警告が表示される場合があります。

初回起動時にWindows Firewallの確認が出た場合は、iPhone/MacとPCが接続されている**プライベートネットワーク**での通信を許可してください。

## 使い方

1. Windows PCとiPhone/Macを同じ家庭内ネットワークへ接続します。
2. APAudioを起動します。
3. iPhone/MacのAirPlay音声出力先から **APAudio Windows** を選択します。
4. 曲の選択、再生・停止、音量変更は送信元から行います。

## 現在の制約

- AirPlay 1 / RAOP の**音声のみ**です。
- AirPlay 2、映像ミラーリングには対応していません。
- 曲情報やアートワークは送信元アプリが送信した場合のみ表示されます。
- 音声出力先は現在Windowsの既定出力デバイスを使用します。
- すべての送信元アプリ・ネットワーク・Windows環境での動作を保証するものではありません。

## 不具合・欲しい機能

GitHub Issuesから送ってください。

- **Bug report** — 不具合報告
- **Feature request** — 欲しい機能、改善案

個人開発のため、返信・修正・機能追加の時期は未定です。要望を送っていただくこと自体は歓迎していますが、実装や期限を約束するものではありません。

## 開発支援

APAudioは無料で使用でき、支援しないことで機能が制限されることはありません。

もし役に立った場合や、今後の改善・機能追加・対応環境の検証を支援したい場合は、GitHubの **Sponsor** ボタンから任意で支援できます。

支援は開発継続の助けとして受け取るもので、特定機能の実装、優先対応、更新時期を保証するものではありません。

## プライバシー

APAudioはAirPlay受信処理をPC上で行います。アカウント登録は不要です。

診断ログはローカルの以下へ保存されます。

`%LOCALAPPDATA%\APAudio\logs\APAudio.log`

ログには互換性調査に必要なプロトコル形状情報を記録する場合がありますが、Active-Remote/DACPトークン値や受信したアートワーク/メタデータ本文を意図的に記録しない設計です。

## ソースからビルド

Java 17 と Gradle 9.4.1 環境で実行できます。

```powershell
gradle :windows:test :windows:installDist
```

Windows用のPortable app image:

```powershell
gradle :windows:jpackageAppImage
```

EXE installerの生成にはWiX Toolset 3が必要です。

詳細は `windows/README.md` を参照してください。

## ライセンス

APAudio本体はMIT Licenseで公開します。

RAOP互換実装で参照したプロジェクト、および同梱しているALAC decoderのライセンス情報は `docs/RAOP_PROVENANCE.md` と `docs/third_party/` を参照してください。
