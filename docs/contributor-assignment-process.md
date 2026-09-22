# Contributor copyright assignment process

Process version: `CAA-PROCESS-1.0`  
Effective date: `2026-09-22`

This is the maintainer runbook for accepting work into hReader. It is an
operational process, not legal advice. The assignment form must be reviewed
for the applicable jurisdiction before it is used.

## Merge rule

Every accepted change has exactly one copyright status:

| Status | Meaning | Merge allowed |
| --- | --- | --- |
| `OWNER` | The project owner authored the work and owns or controls the rights | Yes |
| `ASSIGNED` | An identified external rights holder signed the current assignment and the record is verified privately | Yes |
| `UNRESOLVED` | Any other situation, including an unsigned external change, unclear employer ownership, an unknown AI/tool provenance or missing third-party terms | No |

There is no obvious-fix, typo, documentation, bot, AI or emergency waiver.
If somebody other than the project owner authored a copyrightable change, the
assignment is required before merge.

## Intake workflow

1. **Classify the source.** Identify the human or legal entity that authored
   the change. Commit author fields, bot names and AI tool names are metadata,
   not proof of copyright ownership. Trace the work to its actual rights
   holder.
2. **Check employer and commissioning rights.** Ask whether the work was
   created during employment, consulting, education, a grant or on behalf of
   another organization. Obtain written authority from that rights holder or
   reject the contribution.
3. **Freeze the scope.** Record the PR number and, before merge, the accepted
   commit range or patch. Mark material that is not intended as a contribution
   as `NOT A CONTRIBUTION` before it is exchanged.
4. **Send the current form privately.** Use
   [Contributor Copyright Assignment Agreement](contributor-assignment-agreement.md)
   with its version and effective date. Do not ask for legal names, addresses,
   signatures or employer information in a public issue or pull request.
5. **Verify the signed agreement.** Check the legal name, signature, date,
   identity channel, authority to assign, employer authorization and the
   covered project scope. If any check fails, keep the status `UNRESOLVED`.
6. **Create a private record.** Assign an opaque record ID such as
   `CAA-2026-001`. Store the signed copy and verification evidence in the
   maintainer's access-controlled storage. Never commit the signed copy or
   personal data.
7. **Record the public linkage.** Put only the record ID, PR/commit scope and
   status in the private register. In the PR, select `OWNER` or `ASSIGNED`
   and write the record ID if the maintainer's workflow permits it.
8. **Audit third-party material.** Separate copied or generated code, model,
   voice, dataset, font, image or documentation from the contributor's own
   work. Record its source, revision, license and redistribution conditions in
   the notices inventory. Assignment cannot transfer rights the contributor
   does not own.
9. **Merge only after clearance.** A maintainer may merge only `OWNER` or
   verified `ASSIGNED` work. A review approval, signed commit, DCO line,
   checkbox or public statement never substitutes for the assignment.
10. **Retain the record.** Keep the signed agreement, verification notes and
    accepted scope together. If the contributor later reports an ownership
    problem, stop new merges from that record, preserve the evidence and seek
    legal direction.

## Roles and controls

| Role | Responsibility |
| --- | --- |
| Project owner | Defines the rights holder, signs assignments and decides whether to accept work |
| Maintainer | Verifies provenance, stores the private record and enforces the merge gate |
| External contributor | Signs before merge and discloses employer, third-party and generated material |
| Reviewer | Reviews technical content; does not approve copyright clearance by code review alone |

The owner may delegate technical review, but not the decision to mark an
assignment verified unless the delegate has access to the private record and is
authorized to perform that check.

## Versioning and exceptions

- Changes to the assignment form receive a new form version. New contributors
  sign the current version.
- Existing assignments remain governed by the version that was signed, unless
  a new signed agreement replaces it.
- Corporate, employer-owned or multi-author contributions require a separate
  agreement naming the actual rights holder. Do not rely on one employee's
  personal signature.
- Rejected or abandoned contributions are not assigned by this process. Do not
  merge their code, tests, documentation or generated artifacts later without
  obtaining the required rights.

## Private register fields

Use [the register template](contributor-rights-register.template.md). The
public repository stores only the template, never the completed register.
