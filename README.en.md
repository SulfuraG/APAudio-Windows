**English** | [日本語](README.md)

# APAudio for Windows

**APAudio is a completely free AirPlay 1 / RAOP audio receiver for Windows.**

It lets you use a Windows PC as an AirPlay audio destination for an iPhone, iPad, or Mac, so audio can be played through your PC speakers or connected audio equipment.

> This repository contains the Windows version only.  
> APAudio is an independent project and is not affiliated with or endorsed by Apple Inc.

## Current language support

**The application UI is currently Japanese-only.**

You can still use APAudio from an English-language iPhone, iPad, or Mac, but menus and status text inside the Windows application are currently displayed in Japanese.

English UI support may be added in the future.

## Features

- AirPlay 1 / RAOP audio receiving
- Apple Lossless (ALAC) decoding and PCM playback on Windows
- Song title, artist, album, artwork, and playback progress when provided by the sender
- Sender volume support
- System Tray support
- Closing the main window keeps the receiver running in the tray
- No account required
- No subscription
- No ads
- No paid feature restrictions

## Download

Prebuilt packages are distributed through GitHub Releases.

Available packages:

- **EXE installer** — recommended for most users
- **Portable ZIP** — run without a normal installation

Current application version: **0.3.0**

### Windows SmartScreen warning

The current builds are not code-signed, so Windows SmartScreen may display a warning when launching the EXE installer.

On first launch, Windows Firewall may also ask whether APAudio is allowed to communicate over the network. Allow access on the **private/home network** shared by your sender device and PC.

## How to use

1. Connect your Windows PC and iPhone/iPad/Mac to the same local network.
2. Start APAudio.
3. Open the AirPlay audio output selector on your sender device.
4. Select **APAudio Windows**.
5. Control playback and volume from the sender device.

## Current limitations

- AirPlay 1 / RAOP **audio only**
- No AirPlay 2 support
- No video or screen mirroring
- Metadata and artwork are shown only when the sender provides them
- Audio currently uses the default Windows output device
- Compatibility with every sender app, network, and Windows environment is not guaranteed
- **The Windows application UI is currently Japanese-only**

## Bug reports and feature requests

Please use GitHub Issues.

- **Bug report** — report something that does not work correctly
- **Feature request** — suggest an improvement or new feature

APAudio is maintained as a personal project in spare time, so there is no guaranteed response time, release schedule, or implementation schedule.

Suggestions are welcome, but submitting an issue does not guarantee that a feature will be implemented.

## Support the project

**APAudio is completely free to use.**

There are no paid-only features, subscriptions, or restrictions for users who do not sponsor the project.

If APAudio is useful to you and you would like to support future development, testing, compatibility work, and maintenance, you can optionally support the project through the **Sponsor** button on GitHub.

Sponsorship is entirely voluntary and does not guarantee priority support, a specific feature, or a release date.

## Privacy

APAudio processes AirPlay audio locally on your PC. No account is required.

Diagnostic logs are stored locally at:

`%LOCALAPPDATA%\APAudio\logs\APAudio.log`

The logging system may record protocol-shape information useful for compatibility debugging, but it is intentionally designed not to log Active-Remote/DACP token values or received artwork/metadata payload contents.

## Build from source

Java 17 and Gradle 9.4.1 are recommended.

```powershell
gradle :windows:test :windows:installDist
```

Build a portable Windows application image:

```powershell
gradle :windows:jpackageAppImage
```

Building the EXE installer requires WiX Toolset 3.

See `windows/README.md` for more development and packaging details.

## License

APAudio is released under the **MIT License**.

For information about referenced RAOP implementations and bundled ALAC decoder licensing, see:

- `docs/RAOP_PROVENANCE.md`
- `docs/third_party/`

## Contributing

Bug reports, feature requests, and pull requests are welcome.

Please see `CONTRIBUTING.md` before making changes to the receiver or audio-processing path.
