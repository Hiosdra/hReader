# hReader production third-party notices inventory

Inventory ID: `hreader-notices-2026-09-22.3`  
Snapshot date: `2026-09-22`  
Source tree anchor: `98c75714eed9b17b0c265f3b369ea90e85bd13b7` plus the
working-tree source and notice changes in this release-preparation branch

This is the versioned inventory for the production Android runtime. It is a
release input, not a claim that every model is cleared for redistribution. A
new release must create a new inventory ID when a dependency, native binary,
model URL, revision, archive or license changes.

## Scope and evidence

Included:

- `:app:releaseRuntimeClasspath` resolved on `2026-09-22`;
- the local `sherpa-onnx` AAR and its embedded native libraries;
- the LiteRT-LM Android AAR and its embedded third-party notice;
- model and helper-data downloads referenced by
  `TtsModelPackageCatalog.kt` and the Gemma model source;
- test-only and build-only dependencies are excluded from this runtime
  inventory.

The Gradle evidence command was:

```text
./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain --quiet
```

The dependency report contains requested and selected versions. The tables
below record the selected production roots; transitive components are grouped
by their resolved family where they share the same upstream notice.

## Project code

| Component | Version or scope | License | Notice action |
| --- | --- | --- | --- |
| hReader source code | This repository | GNU AGPL-3.0-only | Keep the full text in [`LICENSE`](../LICENSE) and identify the project code separately from third-party material |

The AGPL applies to hReader-owned code only. It does not relicense libraries,
native runtimes, model weights, voices, datasets or helper data.

## Resolved production libraries

| Family and selected roots | License | Copyright/source | Notice action |
| --- | --- | --- | --- |
| AndroidX and Jetpack: `androidx.activity:activity-compose:1.13.0`, `androidx.browser:browser:1.10.0`, `androidx.core:core-ktx:1.19.0`, `androidx.datastore:datastore-preferences:1.2.1`, `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`, `androidx.lifecycle:lifecycle-process:2.11.0`, `androidx.navigation:navigation-compose:2.10.1`, `androidx.work:work-runtime-ktx:2.11.2`, `androidx.paging:paging-runtime-ktx:3.5.1`, `androidx.paging:paging-compose:3.5.1`, `androidx.room:room-paging:2.8.5`, `androidx.room:room-ktx:2.8.5`, `androidx.room:room-runtime:2.8.5`, `androidx.profileinstaller:profileinstaller:1.4.1` | Apache-2.0 | Android Open Source Project / Google | Include the Apache-2.0 notice and AOSP attribution in the shipped open-source notice |
| Compose: BOM `androidx.compose:compose-bom:2026.09.00`; resolved `foundation:1.12.1`, `material3:1.4.0`, `material-icons-core:1.7.8`, `ui:1.12.1`, `ui-graphics:1.12.1`, `ui-tooling-preview:1.12.1` | Apache-2.0 | Android Open Source Project / Google | Include the Compose/AOSP attribution; the BOM itself is not a runtime class library |
| Kotlin: `org.jetbrains.kotlin:kotlin-stdlib:2.4.20`; resolved `kotlin-reflect:2.4.0` | Apache-2.0 | JetBrains and Kotlin contributors | Include the Kotlin notice |
| Kotlin coroutines: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0`, `kotlinx-coroutines-core-jvm:1.11.0` | Apache-2.0 | Kotlin Foundation and contributors | Include the coroutines notice |
| OkHttp: `com.squareup.okhttp3:okhttp:5.5.0`, `logging-interceptor:5.5.0`; Okio `com.squareup.okio:okio:3.18.1` | Apache-2.0 | Square, Inc. and contributors | Include the Square/OkHttp/Okio notices |
| Retrofit: `com.squareup.retrofit2:retrofit:3.0.0`, `converter-moshi:3.0.0`; Moshi `com.squareup.moshi:moshi-kotlin:1.15.2`, `moshi:1.15.2` | Apache-2.0 | Square, Inc. and contributors | Include the Retrofit/Moshi notices |
| Koin: BOM `io.insert-koin:koin-bom:4.2.2`; `koin-android`, `koin-androidx-compose-navigation`, `koin-androidx-workmanager` resolved at `4.2.2` | Apache-2.0 | InsertKoinIO and contributors | Include the Koin notice |
| Coil: `io.coil-kt.coil3:coil-compose:3.6.2`, `coil-network-okhttp:3.6.2` and resolved Coil Android/core modules | Apache-2.0 | Coil contributors | Include the Coil notice |
| Jsoup: `org.jsoup:jsoup:1.23.2` | MIT | Jonathan Hedley and contributors | Include the MIT copyright and permission text |
| Apache Commons: `org.apache.commons:commons-compress:1.28.0`; resolved `commons-codec:1.19.0`, `commons-io:2.20.0`, `commons-lang3:3.18.0` | Apache-2.0 | Apache Software Foundation | Include the Apache Commons notices |
| Sentry JVM/Android: `io.sentry:sentry-android-core:8.56.0`, `sentry-android-ndk:8.56.0`; resolved `sentry:8.56.0`, `sentry-native-ndk:0.16.6` | MIT | Sentry and contributors | Include both JVM and native Sentry MIT notices |
| LiteRT-LM: `com.google.ai.edge.litertlm:litertlm-android:0.17.0`; resolved `com.google.code.gson:gson:2.14.0` and Kotlin/coroutines dependencies | Apache-2.0 for LiteRT-LM and Gson | Google AI Edge / ODML authors; Gson contributors | Preserve the exact AAR `LICENSE` and `THIRD_PARTY_NOTICE.txt`; see native section |

Selected transitive libraries such as Accompanist `0.37.3`, Touchlab Stately
`2.1.0`, Kotlin serialization `1.7.3`, Guava ListenableFuture `1.0`, JSpecify
`1.0.0`, Error Prone annotations `2.48.0` and JetBrains annotations `23.0.0`
are included in the resolved graph and must remain covered by the generated
Apache-2.0 family notice. The release artifact scan must confirm that no
variant-specific component is omitted.

Upstream license references:

- [AndroidX source license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE)
- [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [Kotlin coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)
- [OkHttp license](https://github.com/square/okhttp/blob/master/LICENSE.txt)
- [Retrofit license](https://github.com/square/retrofit/blob/master/LICENSE)
- [Moshi license](https://github.com/square/moshi/blob/master/LICENSE)
- [Koin license](https://github.com/InsertKoinIO/koin/blob/main/LICENSE)
- [Coil license](https://github.com/coil-kt/coil/blob/main/LICENSE.txt)
- [Jsoup license](https://jsoup.org/license)
- [Apache Commons Compress license](https://github.com/apache/commons-compress/blob/master/LICENSE.txt)
- [Sentry JVM license](https://raw.githubusercontent.com/getsentry/sentry-java/main/LICENSE)
- [Sentry native license](https://raw.githubusercontent.com/getsentry/sentry-native/master/LICENSE)
- [LiteRT-LM repository](https://github.com/google-ai-edge/LiteRT-LM)

## Native runtimes

### Local sherpa-onnx AAR

| Artifact | Evidence | License and action |
| --- | --- | --- |
| `app/libs/sherpa-onnx-1.13.4-arm64.aar` | 11,822,046 bytes; SHA-256 `eaf71494b5246b5338091683868cfeed00b3a6325a893380f9c72f01224e6748` | sherpa-onnx source is Apache-2.0. The AAR itself contains no `LICENSE`, `NOTICE` or `COPYING` file; hReader now ships the upstream license at `assets/licenses/sherpa-onnx/LICENSE` |
| `libonnxruntime.so` inside that AAR | Bundled in `jni/arm64-v8a/`; binary exports `VERS_1.27.0` | The producing sherpa-onnx v1.13.4 release records ONNX Runtime 1.27.0. hReader ships the matching MIT license and full `ThirdPartyNotices.txt` under `assets/licenses/sherpa-onnx/onnxruntime/` |
| `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so` | Same AAR and hash | Apache-2.0 sherpa-onnx code; keep the copyright and license shipped under `assets/licenses/sherpa-onnx/` |

The AAR must not be described only as “Apache-2.0 sherpa-onnx”: it embeds a
separate ONNX Runtime native library. The binary evidence and the producing
release identify it as ONNX Runtime 1.27.0. The exact native notice bundle is
now stored in the application assets and remains tied to the AAR hash.
Upstream source/release reference:
[sherpa-onnx v1.13.4](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4).

### LiteRT-LM Android AAR

| Artifact | Evidence | License and action |
| --- | --- | --- |
| `litertlm-android:0.17.0` | AAR size 20,492,644 bytes; SHA-256 `28aa6bc43efcee35b31795f9e5ca633c3dec06d9f3fb85ecb6a753fa360e2134` | The AAR contains Apache-2.0 `LICENSE` (SHA-256 `c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4`) |
| `THIRD_PARTY_NOTICE.txt` inside the same AAR | 39,917 lines, 2,146,747 bytes; SHA-256 `67d807a83a6e4f9457365ca61ff3fa1db95b135a17feeefadeff8e9f02123243` | Exact notice copied to `assets/licenses/litert/THIRD_PARTY_NOTICE.txt` |

The AAR's embedded third-party file contains AndroidX and other Apache-2.0
attributions. Its SHA is recorded here so a future cache refresh cannot silently
change the notice set.

## Downloaded models and helper data

The hashes and sizes below are the pins currently present in
`TtsModelPackageCatalog.kt`. Model licenses do not become hReader-owned just
because hReader downloads or converts the files.

| Artifact | Exact pin | Verified finding | Release status |
| --- | --- | --- | --- |
| Gemma 4 E2B LiteRT-LM | `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94/gemma-4-E2B-it.litertlm`; SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | The official Gemma 4 model card and Gemma 4 Apache page identify this family as Apache-2.0. The selected model revision is pinned in the app source; attribution is bundled in `assets/licenses/models/gemma-4/`. | Yellow pending final APK/AAB asset and UI scan |
| Supertonic 3 int8 | `supertonic-3-int8-2026-05-11.tar.bz2`; SHA-256 `2b9f11979b0b0f85a2653e63ea567bc819994822e0ff3b7b5ee1b06068fc5c78`; 128,784,503 bytes; HF LICENSE SHA-256 `0d944a9110fed9a9602d60e0423a272903e7bd21ab060490774efc77c2275e9f` at revision `724fb5abbf5502583fb520898d45929e62f02c0b` | OpenRAIL-M terms are downloaded with the model and the complete pinned license is bundled under `assets/licenses/models/supertonic-3/`. | Yellow pending final APK/AAB asset and UI scan |
| Coqui Polish VITS `mai_female` | HF revision `19e9a1a8e697491e5b9849f8f904584792d60fb8`; `model.onnx` SHA-256 `74bd98e84c42961d58b3523ec2a9a98549480b23c3d9e8e1dd52e2f03eb00575` (71,024,119 bytes); `tokens.txt` SHA-256 `4b4ba3385bf661b87735835de24365e25623d250be0621bc184dff050df4c2fb` (1,410 bytes) | Coqui metadata identifies the model as BSD-3-Clause. The metadata provenance and full BSD-3-Clause text are bundled under `assets/licenses/models/coqui-pl-mai-female/`. | Yellow pending final APK/AAB asset and UI scan |
| Kokoro v1.1 multilingual int8 | `kokoro-int8-multi-lang-v1_1.tar.bz2`; SHA-256 `a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6`; 147,031,220 bytes | Archive contains `LICENSE` and README. The embedded license SHA-256 is `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` and is Apache-2.0 text. Model and helper-data notices are bundled under `assets/licenses/models/kokoro/` and `assets/licenses/models/helpers/`. | Yellow pending final APK/AAB asset and UI scan |
| Kokoro v1.0 multilingual | `kokoro-multi-lang-v1_0.tar.bz2`; SHA-256 `c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298`; 349,906,910 bytes | Archive contains the same Apache-2.0 `LICENSE` (SHA-256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`) and a README that attributes the voices to the official Kokoro-82M source. Model and helper-data notices are bundled under `assets/licenses/models/kokoro/` and `assets/licenses/models/helpers/`. | Yellow pending final APK/AAB asset and UI scan |
| KittenTTS mini v0.8 | `kitten-mini-en-v0_8.tar.bz2`; SHA-256 `518f9b130320f690d5b5476df77bde4215fca67773cda16710318e5081234b9d`; 67,547,594 bytes | Archive contains a full Apache-2.0 `LICENSE` and README pointing to KittenML. The model and eSpeak NG helper-data notices are bundled under `assets/licenses/models/`. | Yellow pending final APK/AAB asset and UI scan |

Helper-data rules:

- `espeak-ng-data` is a distributed runtime component. eSpeak NG identifies
  itself as GPL-3.0-or-later; retain the GPL notice and source reference.
- `dict` in the Kokoro archives comes from cppjieba-related data. CppJieba is
  MIT; retain that attribution separately from the sherpa and Kokoro notices.
- `tokens.txt`, lexicons, FSTs and voice tables are not automatically covered
  by the acoustic-model license. Keep their provenance with the exact archive.

## Release gate represented by this inventory

The inventory work closes the missing versioned record and bundles the current
model/helper notices, but it does not make the release legally complete. Before
a public/commercial release:

1. manually smoke-test the legal screen and confirm that every listed asset
   opens from the final APK/AAB;
2. generate or otherwise confirm coverage for any resolved Gradle runtime
   component not already represented by the grouped native/asset notices;
3. rerun this inventory against the final release APK/AAB and archive the
   result with the exact artifact SHA-256; and
4. repeat the audit whenever a model URL, revision, archive or native binary
   changes.

## Primary evidence

- [sherpa-onnx source and Apache-2.0 license](https://github.com/k2-fsa/sherpa-onnx)
- [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4)
- [Gemma 4 Apache-2.0 terms](https://ai.google.dev/gemma/apache_2)
- [Kokoro v1.0 model card](https://huggingface.co/hexgrad/Kokoro-82M)
- [Kokoro v1.1-zh model card](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh)
- [KittenTTS Apache-2.0 license](https://github.com/KittenML/KittenTTS/blob/main/LICENSE)
- [LJ Speech public-domain statement](https://keithito.com/LJ-Speech-Dataset/)
- [Blizzard 2013 Lessac research license](https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html)
- [eSpeak NG GPL-3.0-or-later statement](https://github.com/espeak-ng/espeak-ng)
- [CppJieba MIT license](https://github.com/yanyiwu/cppjieba/blob/master/LICENSE)
- [ONNX Runtime 1.27.0 MIT license](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.27.0/LICENSE)
- [ONNX Runtime 1.27.0 third-party notices](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.27.0/ThirdPartyNotices.txt)
