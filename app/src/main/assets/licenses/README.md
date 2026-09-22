# hReader bundled third-party licenses

These files are bundled with hReader because the release includes the
corresponding binary or downloaded runtime.

The active model and helper-data notices are under `models/`. The in-app legal
screen lists those documents together with the native/runtime notices below.

## LiteRT-LM 0.17.0

`litert/LICENSE` and `litert/THIRD_PARTY_NOTICE.txt` are copied verbatim from
the resolved `com.google.ai.edge.litertlm:litertlm-android:0.17.0` AAR.

The AAR SHA-256 is
`28aa6bc43efcee35b31795f9e5ca633c3dec06d9f3fb85ecb6a753fa360e2134`.
The embedded notice SHA-256 is
`67d807a83a6e4f9457365ca61ff3fa1db95b135a17feeefadeff8e9f02123243`.

## sherpa-onnx 1.13.4

`sherpa-onnx/LICENSE` is the upstream Apache-2.0 license for the local AAR.
The AAR SHA-256 is
`eaf71494b5246b5338091683868cfeed00b3a6325a893380f9c72f01224e6748`.

The AAR contains ONNX Runtime in `libonnxruntime.so`. The binary exports
`VERS_1.27.0`, matching the ONNX Runtime version recorded by the sherpa-onnx
v1.13.4 release. The corresponding MIT license and complete upstream
third-party notice are in `sherpa-onnx/onnxruntime/`.
