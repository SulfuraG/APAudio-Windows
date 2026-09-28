# APAudio for Windows

This module is the Windows/JVM desktop receiver for APAudio.

## Receiver path

- Advertise `_raop._tcp` on the active Windows LAN interface.
- Accept AirPlay 1 / RAOP RTSP sessions on TCP port 5000.
- Include the proven Android-independent SDP, RSA/AES, RTP, ALAC and metadata code directly in the Windows source tree.
- Decode Apple Lossless audio to PCM16 stereo.
- Play PCM through the Windows default Java Sound output device.
- Apply sender volume in software and remember the latest sender volume.

The desktop shell adds:

- A Swing Now Playing window with connection state, metadata, artwork, progress and sender volume.
- A System Tray icon with Open, current receiver status and Exit actions.
- Window close-to-tray behavior while the receiver remains active.
- Clean application-owned shutdown of mDNS, RTSP, active sessions, UDP and Java Sound.

The Windows project is self-contained and does not require the Android application source tree.

## Sender compatibility

The receiver is intentionally sender-agnostic at the RAOP layer. The verified iPhone path and the observed macOS audio path both use the same RSA/AES, RTP, ALAC and Java Sound pipeline.

Full Now Playing information depends on what the AirPlay source actually supplies. Music/iTunes-style sources can provide DMAP metadata and artwork, while other sources may provide little or no media metadata even when audio streaming itself works.

For macOS sender compatibility work, packaged logs record only non-sensitive protocol shape information: User-Agent, presence of Client-Instance/DACP-ID/Active-Remote headers, SET_PARAMETER content types and body sizes, text parameter names, and the four-byte DMAP root tag. Active-Remote/DACP token values and metadata/artwork payload contents are deliberately not logged.

Runtime logs are under:

`%LOCALAPPDATA%\APAudio\logs\APAudio.log`

## Run from Windows

```powershell
gradle :windows:run
```

On the first launch, Windows Firewall may ask whether Java can access the network. Allow access on the private/home network used by the sender and PC.

Then select `APAudio Windows` from the sender's AirPlay audio output picker.

## Build and test

```powershell
gradle :windows:test :windows:installDist
```

The launcher is generated under:

`windows\build\install\windows\bin\windows.bat`

## Build native Windows packages

The package version is defined once in `windows/build.gradle.kts`. `jpackage`
uses that version for the application image, native launcher and installer.

Build the portable application image with the Java 17 runtime included:

```powershell
gradle :windows:jpackageAppImage
```

The result is:

`windows\build\jpackage\app-image\APAudio\APAudio.exe`

No separately installed Java runtime is needed. The generated runtime contains
the stable standard modules needed by the Swing receiver: `java.base`,
`java.desktop` and `java.prefs` (plus their transitive modules).

The EXE installer requires WiX Toolset 3. WiX 3.14.1 portable binaries can be
expanded under `windows\build\tools\wix314`, or supplied with `-PwixHome`:

```powershell
gradle :windows:jpackageInstaller `
  -PwixHome="C:\path\to\wix314"
```

The generated installer is versioned under:

`windows\build\jpackage\installer\APAudio-<version>.exe`

It installs APAudio for all users under Program Files, registers it in Windows
Installed Apps, and creates a Start Menu entry in the `APAudio` group. Building
locally does not code-sign the installer; release signing can be added when a
Windows code-signing certificate is available.

Closing the main window hides it to the System Tray. Use `APAudioを開く` (or activate the tray icon) to restore it. `終了` is the normal action that stops the receiver process.

## Current scope

The receiver continues to use the Windows default Java Sound device. Output
device selection, WASAPI, settings, auto-start, advanced installer work and
AirPlay 2 remain outside this phase. Uninstall removes installed application
binaries while leaving normal per-user preferences and logs under
`%LOCALAPPDATA%\APAudio` intact.
