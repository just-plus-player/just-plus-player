The binary ffmpeg extension was build with following decoders:

```
ENABLED_DECODERS=(vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd)
```

Complete [build instructions](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md).

To assemble ``.aar``:

```
./gradlew :extension-ffmpeg:bundleReleaseAar
```

## lib-decoder-av1-release.aar is NOT upstream's any more

Its four `libdav1dJNI.so` were rebuilt here on 2026-09-19. Everything else in the AAR - the Java
classes, the manifest, proguard.txt - is still upstream's file, untouched.

**Why.** Through media3 1.11.1 the bundled dav1d JNI refuses every frame deeper than 8 bits, in both
output modes:

```c
if (dav1d_picture->p.bpc != 8) {
  context->jni_status_code = kJniStatusHighBitDepthNotSupportedWithYuv;
  return kStatusError;
}
```

So a 10-bit AV1 track opens the decoder and dies on the first frame, and a phone whose platform AV1
decoder stops at 1920x1072 (a Mi Note 10 does) has nothing left that can show a 4K HDR AV1 file.
The 10-bit path - P010 straight into the Surface, with the dataspace set from the transfer function -
landed on `androidx/media` main after 1.11.1 and is in no release yet.

**What was built.** `libraries/decoder_av1/src/main/jni/` from `androidx/media` main, unmodified:
`dav1d_jni.cc`, `cpu_info.cc/h`, `CMakeLists.txt`, plus `cpu_features` (google) and dav1d 1.5.4
(videolan, tag-less HEAD of 2026-09-16). NDK 29.0.14206865, CMake 3.31.6, `-DCMAKE_BUILD_TYPE=Release`,
`android-21`, then `llvm-strip --strip-unneeded`. dav1d itself was built with meson per ABI exactly as
`build_dav1d.sh` does it. arm64-v8a and armeabi-v7a carry their assembly; x86 and x86_64 were built
with `-Denable_asm=false` because there was no nasm on the build machine - they are emulator-only (the
release APK ships the two ARM ABIs), and the C fallback is correct, only slower.

It is safe against the Java it sits next to: main's JNI registers exactly the eleven native methods
this AAR's `Dav1dDecoder` declares, `releaseUnusedInputBuffers` included, with identical signatures,
and every field and method it looks up exists in media3-decoder 1.11.0.

**Delete this section** when upstream ships a release with the 10-bit path and the AARs are next taken
from moneytoo/Player - at that point `git checkout <commit> -- app/libs/lib-*.aar` puts the file back
to being upstream's, byte for byte, and this note stops being true.
