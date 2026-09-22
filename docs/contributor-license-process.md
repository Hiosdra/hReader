# Contributor license process

Process version: `CLA-PROCESS-1.0`  
Effective date: `2026-09-22`

This is an operational runbook, not legal advice. The agreement must be
reviewed for the relevant jurisdiction before it is signed.

## Merge rule

Every external contribution has exactly one status:

| Status | Meaning | Merge allowed |
| --- | --- | --- |
| `OWNER` | The project owner authored the work and controls its rights | Yes |
| `CLA_ON_FILE` | The external rights holder signed the current CLA and the private record was verified | Yes |
| `UNRESOLVED` | The CLA is missing, employer ownership is unclear, or provenance/third-party terms are incomplete | No |

There is no obvious-fix, typo, documentation, bot, AI or emergency waiver for
external work. If somebody other than the project owner authored a
copyrightable change, the CLA must be on file before merge.

## One-time intake

1. Identify the actual human or legal entity that authored the work.
2. Check employer, client, school, grant and other ownership obligations.
3. Send the current [Contributor License Agreement](contributor-license-agreement.md)
   through a private channel.
4. Verify the legal name, signature, date, authority to grant rights and the
   CLA version.
5. Store the signed copy and verification evidence in access-controlled
   storage. Never commit signatures, addresses or identity documents.
6. Assign an opaque private record ID such as `CLA-2026-001`.
7. Mark the contributor `CLA_ON_FILE` in the maintainer's private register or
   workflow. The same agreement covers later accepted Contributions.
8. Require the contributor to disclose third-party code, models, datasets,
   fonts, generated material and other restrictions for every PR.
9. Merge only when the CLA status and provenance checks are clear.

The CLA is a one-time rights intake, not a transfer of copyright. The
Contributor keeps ownership but gives the Project Owner the broad license
needed to distribute the AGPL project, commercial versions, dual-licensed
versions and future relicensed versions.

## Corporate and employer-owned work

If an employer or other entity owns the Contribution, the individual must not
sign on its behalf without authority. Obtain a corporate agreement or written
permission from the actual rights holder. Record only the private agreement
ID and status in the public workflow.

## Third-party and generated material

The CLA cannot grant rights the contributor does not own. Review the source,
revision, license, model terms, generated-content terms and redistribution
conditions of every copied or generated component. Update the appropriate
notices before release.

## Versioning and retention

- New agreement wording receives a new version. New contributors sign the
  current version.
- Existing contributions remain covered by the version that was signed.
- A contributor may stop making future contributions, but cannot revoke the
  grant for an accepted Contribution.
- Rejected or abandoned work is not accepted into the Project under this
  process.
- Keep the signed agreement, verification notes and accepted-contribution
  linkage privately for as long as the Project Owner needs to exercise or
  defend the rights.
