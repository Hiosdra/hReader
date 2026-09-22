# hReader third-party notices

Inventory ID: [`hreader-notices-2026-09-22.3`](docs/third-party-notices-inventory-2026-09-22.md)  
Snapshot date: `2026-09-22`

The hReader project code is licensed under GNU AGPL-3.0-only; the full text is
in [`LICENSE`](LICENSE). Third-party libraries, native runtimes, models,
voices, datasets and helper data retain their own terms and are not relicensed
by this file.

The complete versioned production inventory is in
[`docs/third-party-notices-inventory-2026-09-22.md`](docs/third-party-notices-inventory-2026-09-22.md).
It records selected Gradle runtime roots, exact local/native artifact hashes,
downloaded model pins, embedded archive licenses and remaining release gates.

## Current release blockers

- Matcha, Piper Lessac and Vocos are not supported or distributed by hReader.
- The local sherpa AAR embeds ONNX Runtime 1.27.0. Its MIT license and full
  third-party notice are shipped under `app/src/main/assets/licenses/`.
- LiteRT-LM's exact `THIRD_PARTY_NOTICE.txt` is shipped under
  `app/src/main/assets/licenses/litert/`.
- Active-model and helper-data notices are shipped under
  `app/src/main/assets/licenses/models/` and are wired into the user-visible
  legal surface.
- The remaining release work is a final UI/APK/AAB artifact check and any
  missing generated notice for a resolved Gradle runtime component.

The TTS-specific notice remains at
[`app/src/main/assets/tts/NOTICE`](app/src/main/assets/tts/NOTICE) and must stay
synchronized with the versioned inventory.
