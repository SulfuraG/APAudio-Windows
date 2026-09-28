# Contributing

Bug reports, feature requests and pull requests are welcome.

APAudio is maintained in spare time, so review or merge timing is not guaranteed.

## Before changing receiver processing

The RAOP audio path has been validated against real sender/device behavior. Changes to protocol handling, RSA/AES processing, RTP handling, ALAC decoding or audio delivery should include a clear reason and reproducible evidence.

Please avoid speculative compatibility changes that alter already working behavior without a test or observed protocol difference.

## Pull requests

- Keep changes focused.
- Explain what changed and why.
- Run `.\gradlew.bat :windows:test` when possible.
- Do not include secrets, private tokens, device identifiers or personal logs.
- Preserve third-party copyright/license notices.
