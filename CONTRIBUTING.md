# Contributing to hReader

Thank you for your interest in hReader. The project code is licensed under
GNU AGPL-3.0-only. Third-party libraries, runtimes, models, voices and data
keep their own licenses.

## Contributor License Agreement

Before an external contribution is merged, the contributor must sign the
current [hReader Contributor License Agreement](docs/contributor-license-agreement.md)
through a private channel. The CLA is copyright-retaining: the contributor
keeps ownership, while the project owner receives a perpetual, worldwide,
irrevocable license to modify, distribute, sublicense, commercialize and
relicense accepted contributions.

One signed agreement covers future accepted contributions. The maintainer
stores the signed copy and verification record privately; do not put signatures,
addresses, employer details or identity documents in a PR.

The [contributor license process](docs/contributor-license-process.md) is the
merge gate. There is no paperwork waiver for trivial, obvious, bot,
AI-generated or emergency changes authored by an external contributor.

If an employer, client, school or another legal entity owns the contribution,
the actual rights holder must authorize or sign it. A personal signature is not
enough.

## Before opening a pull request

1. Keep credentials, tokens, personal data, confidential material and private
   feeds out of commits and issue comments.
2. Identify every copied or generated third-party code fragment, model, voice,
   font, image, dataset or documentation asset, including its exact source,
   version, license and redistribution restrictions.
3. Keep third-party material separate from hReader-owned code where possible.
4. Keep changes small and explain their user-visible or architectural effect.

## Code and repository standards

- Follow the architecture and style rules in `AGENTS.md`.
- Do not add dependencies without discussion.
- Do not weaken offline, privacy, secret-storage or backend-boundary guarantees.
- Keep secrets out of source, logs, tests and screenshots.
- Update the relevant notice when adding or changing a distributed library,
  model, voice, runtime or data package.
- For Room schema changes, add the version bump and migration together.
- Preserve the Paging 3 and SQL filtering design.

## Validation

Before requesting review, run the checks relevant to the change. For a normal
code change, the repository baseline is:

```text
./gradlew lint test assembleDebug
```

Describe any check that could not be run and why. Do not include generated
build output or local secrets in the pull request.

This guideline and the agreement template are not legal advice.
