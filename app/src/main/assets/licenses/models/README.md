# Active model and helper-data notices

This directory contains the notices and license texts for the model assets
that hReader currently supports or downloads. The model archives remain
separately licensed from the hReader AGPL-3.0-only source code.

The app's legal screen exposes these files together with the native-runtime
notices under `licenses/`. The hashes below identify the model packages pinned
by the application at this inventory snapshot.

## Gemma 4 E2B

- Model: `litert-community/gemma-4-E2B-it-litert-lm`
- Revision: `6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94`
- Model SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- Terms: Apache-2.0
- Files: `gemma-4/LICENSE`, `gemma-4/MODEL_CARD.md`
- Sources: [pinned model card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94), [Google Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4)

## Supertonic 3

- Model archive SHA-256: `2b9f11979b0b0f85a2653e63ea567bc819994822e0ff3b7b5ee1b06068fc5c78`
- Terms: OpenRAIL-M
- Files: `supertonic-3/LICENSE`, `supertonic-3/NOTICE.md`
- Sources: [model repository](https://huggingface.co/Supertone/supertonic-3), [pinned license](https://huggingface.co/Supertone/supertonic-3/blob/724fb5abbf5502583fb520898d45929e62f02c0b/LICENSE)

## Coqui Polish voice

- Model: `csukuangfj/vits-coqui-pl-mai_female`
- Revision: `19e9a1a8e697491e5b9849f8f904584792d60fb8`
- Model SHA-256: `74bd98e84c42961d58b3523ec2a9a98549480b23c3d9e8e1dd52e2f03eb00575`
- Terms: BSD-3-Clause according to the pinned Coqui model metadata
- Files: `coqui-pl-mai-female/LICENSE`, `coqui-pl-mai-female/NOTICE.md`
- Sources: [pinned model tree](https://huggingface.co/csukuangfj/vits-coqui-pl-mai_female/tree/19e9a1a8e697491e5b9849f8f904584792d60fb8), [Coqui model metadata](https://github.com/coqui-ai/TTS/blob/dev/TTS/.models.json)

## Kokoro and KittenTTS

Kokoro and KittenTTS archives include their own Apache-2.0 license text and
also carry helper data with separate attribution requirements. The complete
helper texts are under `helpers/`.

- `kokoro/NOTICE.md`, `kokoro/LICENSE`: Kokoro v1.1 and v1.0 archives
- `kitten/NOTICE.md`, `kitten/LICENSE`: KittenTTS mini v0.8 archive
- `helpers/espeak-ng/NOTICE.md`, `helpers/espeak-ng/COPYING`: eSpeak NG data
- `helpers/cppjieba/NOTICE.md`, `helpers/cppjieba/LICENSE`: cppjieba-derived data

The supported archive hashes are recorded in the two model notice files and
in `docs/third-party-notices-inventory-2026-09-22.md`.
