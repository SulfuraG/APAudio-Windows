# RAOP protocol provenance

The Apple-Challenge response behavior and RSA compatibility key used by
`AppleChallengeResponse.kt` were independently reimplemented in Kotlin from
the current Shairport Sync source at commit
`fadd43d06e8685c7cb508042f5716f25828a77d8` (observed 2026-08-23):

- Challenge payload, IPv4/IPv6 handling, RSA operation, and unpadded Base64:
  <https://github.com/mikebrady/shairport-sync/blob/fadd43d06e8685c7cb508042f5716f25828a77d8/rtsp.c#L3709-L3766>
- Published AirPort Express RSA compatibility key and PKCS#1 padding mode:
  <https://github.com/mikebrady/shairport-sync/blob/fadd43d06e8685c7cb508042f5716f25828a77d8/common.c#L784-L846>
- AirPlay 1 SETUP parsing, UDP port allocation, and Transport response:
  <https://github.com/mikebrady/shairport-sync/blob/fadd43d06e8685c7cb508042f5716f25828a77d8/rtsp.c#L3055-L3164>
- RSA-OAEP AES key recovery and packet-level AES-CBC handling:
  <https://github.com/mikebrady/shairport-sync/blob/fadd43d06e8685c7cb508042f5716f25828a77d8/rtsp.c#L3461-L3578>
  and
  <https://github.com/mikebrady/shairport-sync/blob/fadd43d06e8685c7cb508042f5716f25828a77d8/player.c#L1558-L1583>

Shairport Sync is distributed under the MIT License. Its copyright and license
notice are available at:
<https://github.com/mikebrady/shairport-sync/blob/fadd43d06e8685c7cb508042f5716f25828a77d8/LICENSES>

No Shairport Sync C implementation was copied. The protocol-compatible RSA key
is stored as PKCS#8 Base64 so it can be loaded by standard JVM cryptography APIs.

## Pure Java ALAC decoder core

Only the decoder core files `AlacFile.java` and `LeadingZeros.java` were vendored
from `vavi-sound-alac` commit
`d84bd5a46426391fea1648a68cedb7ea0fd0241e`:

- <https://github.com/umjammer/vavi-sound-alac/blob/d84bd5a46426391fea1648a68cedb7ea0fd0241e/src/main/java/com/beatofthedrum/alacdecoder/AlacFile.java>
- <https://github.com/umjammer/vavi-sound-alac/blob/d84bd5a46426391fea1648a68cedb7ea0fd0241e/src/main/java/com/beatofthedrum/alacdecoder/LeadingZeros.java>

The package was changed to `com.example.apaudio.alac`, and Java 9
`System.Logger` calls were replaced with `System.err` to retain Android API 24
compatibility. MP4 demuxing, Java Sound integration, file I/O, and WAV helpers
were not imported. The upstream BSD license is reproduced at
`docs/third_party/vavi-sound-alac-LICENSE.txt`.

`RaopAlacDecoder.kt` adds eight zero-filled guard bytes before invoking the
vendored decoder. This keeps its speculative bit-reader look-ahead inside a
bounded adapter without changing the vendored source or the decoded frame
length.
