# Contributing to hReader

Thank you for your interest in hReader. The project code is licensed under
[GNU AGPL-3.0-only](LICENSE). Third-party libraries, runtimes, models, voices
and data keep their own licenses; see [NOTICE.md](NOTICE.md).

## Copyright agreement

The project currently has one author and maintainer. Contributions from the
project owner do not require a separate assignment.

Before an external contribution is accepted or merged, the contributor must
sign the current [Contributor Copyright Assignment Agreement](docs/contributor-assignment-agreement.md)
through a private channel designated by the maintainer. The public template is
not a signature form and signed copies must not be committed to GitHub.

The maintainer follows the [operational assignment process](docs/contributor-assignment-process.md)
and keeps the completed [rights register](docs/contributor-rights-register.template.md)
privately.

The agreement is required before merge for copyrightable code, documentation,
tests, build files, artwork, translations and other original project material.
There is no obvious-fix or trivial-typo waiver. If a change is authored by
someone other than the project owner and is accepted into the repository, the
contributor must sign the assignment before merge.

Opening a pull request, submitting a patch, signing a commit, or participating
in an issue does not by itself transfer copyright to the project owner. The PR
checkbox only records process status; it is not the assignment agreement.

If a contribution was created as part of employment, consulting, a school
project or a grant, the contributor must obtain authorization from the actual
rights holder. A personal signature is not enough when the employer or another
entity owns the work.

The project owner keeps a private record of accepted assignments, contributor
identity, covered PR or commit, employer status and any third-party material.
The record is not published in the repository.

## Before opening a pull request

1. For a new external contributor, request the current agreement from the
   maintainer and complete it privately before the contribution is merged.
2. Keep credentials, tokens, personal data, confidential material and private
   feeds out of commits and issue comments.
3. Identify every copied or generated third-party component, model, voice,
   font, image, dataset or code fragment, including its exact source, version,
   license and redistribution restrictions.
4. Keep third-party material separate from hReader-owned code where possible.
5. Keep changes small and explain the user-visible or architectural effect.

## Pull request requirements

The pull request template must be completed. In particular, select exactly one
copyright status:

- the pull request contains only work authored by the project owner; or
- the external contributor has signed the current assignment and the maintainer
  has recorded it privately.

Do not include a scan, signature, address, employer details or other personal
information in the pull request. The maintainer will request it privately if it
is needed to establish rights.

The maintainer must not merge any contribution while its copyright status is
unresolved. A contribution with unclear provenance, incompatible license terms
or undisclosed third-party material may be rejected or requested as a separate
dependency instead.

## Code and repository standards

- Follow the architecture and style rules in `AGENTS.md`.
- Do not add dependencies without discussion.
- Do not weaken offline, privacy, secret-storage or backend-boundary
  guarantees.
- Keep secrets out of source, logs, tests and screenshots.
- Update `NOTICE.md` and the relevant asset notice when adding or changing a
  distributed library, model, voice, runtime or data package.
- For Room schema changes, add the version bump and migration together.
- Do not load the entire article list into memory; preserve the Paging 3 and
  SQL filtering design.

## Validation

Before requesting review, run the checks relevant to the change. For a normal
code change, the repository baseline is:

```text
./gradlew lint test assembleDebug
```

Do not include generated build output or local secrets in the pull request.
Describe any check that could not be run and why.

## Legal scope

This guideline describes the repository process and is not legal advice. The
assignment template must be adapted and reviewed for the applicable
jurisdiction, employer rights, moral rights and signature requirements before
being used. The project owner may update the agreement or require a separate
corporate agreement for contributions made on behalf of an organization.

For background on why some large free-software projects use copyright
assignment, see the [FSF explanation](https://www.gnu.org/licenses/why-assign.html)
and the [GNU Emacs contribution guidance](https://www.gnu.org/software/emacs/manual/html_node/emacs/Copyright-Assignment.html).
