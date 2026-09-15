# TikTok multi-version migration

## Starting evidence (2026-09-14)

Work starts from `dev` at `0c29f12ce3fa8862f06d46e9030a9bad185d2296`.
Its native patch sources equal the device-confirmed feature head
`232bf9c28134db0b9bf2f9e8cf4a3f8021681667`. The difference is release metadata
and release publication safeguards. Main stays at `714eaa2cc4f42a50f5688499a5025a7fac14a4bf`.
PR #6 remains draft and is not merged. The older 46.4.3 port/crash branches
contain historical fixes superseded in the accepted source; they are not migration bases.
No multi-version feature branch existed at inspection.

Release `v1.2.0-dev.24` is published with an uploaded 1,744,792-byte MPP,
SHA-256 `fe0f0693f7be8d6a3d8bf1d77a6d78e8e1f247ec67ee4bbfed7ddb7eb9614e07`.
Release run 34843421658 succeeded on promotion `a792458261d7d2facd113ef938c631e489d3ea99`.
Acceptance 34620836565 and discovery 34620836595 succeeded on the exact feature head.
The unrelated automatic PR-to-main job failed; this did not prevent publishing the release.

## Architecture and boundaries

1. `fixtures.json` identifies each original APK by package, version, version code,
   SHA-256, origin and qualification evidence. Only qualified entries feed Morphe
   compatibility. Candidate fixtures can run CI without becoming selectable.
2. The source inventory includes every TikTok patch, fingerprint declaration,
   native reference, literal, register expression, instruction selection and runtime
   reflection site, with source locations. This lexical audit surface is not a claim
   that every literal is a hook. Runtime resolution evidence is a separate artifact.
3. `TikTokFingerprint` uses the existing Morphe predicate implementation, but all
   consumers require cardinality before receiving a mutable method. Optional means
   zero is allowed; multiple matches still fail. Multi-site hooks explicitly request
   a set and record every method. Resolution is cached per APK session, before injection.
4. Shared injection helpers retain target labels, validate instruction boundaries,
   derive invocation/result/parameter registers from bytecode and signatures, and
   raise `PatchException` for broken contracts. Version-specific semantics such as
   the eligible clear-display completion path stay constrained until separately proven.
5. Original bytecode is captured before catalog mutations. Reports include selectors,
   normalized signatures, resolved members and injection evidence. Normalization removes
   obfuscated names and register allocation, while preserving stable API/string anchors.
   Discovery separates `resolved`, `relocated`, `ambiguous`, `missing`, and
   `contract-changed`. A relocated candidate is evidence for review, not permission
   to activate a version.
6. Every fixture runs package/version/hash checks, bundle build, all 37 catalog patches,
   bytecode/runtime regression tests, discovery and a same-APK self-comparison. Reports
   are stored separately per version and carry the tested feature SHA (not a PR merge SHA).

Central TUX/Compose resolvers, the classic profile, native drawer registration and
default palette restoration remain the behavioral baseline. No startup DEX scanner,
visible-text matching, geometry search or broad recoloring is introduced.

## Qualification

Add an APK as a candidate with its exact hash and provenance; run discovery and the
matrix; resolve ambiguous/changed contracts; record successful same-head evidence
and relevant device regression results; only then mark the fixture selectable.
Never infer validation for another APK from a version name, similar build or old CI.
Morphe compatibility metadata lists version names, not hashes; CI evidence must always
be interpreted together with the fixture identity. Unlisted APK bytes are not certified.

The initial fixture remains TikTok global 46.7.3, SHA-256
`b9e96e64e94ac0f9ea229dd0ba743f1930121a8b6941cf9fd87191604da0129e`.
No additional version is claimed. New source still needs its own matrix result even
though that original baseline was already accepted on a device.

## Review order

Inventory and qualification manifest; shared resolution/injection contracts;
feed/download/gesture migrations; settings/theme and remaining catalog migrations;
discovery/matrix and tests. Keep commits reviewable. Do not promote to dev or release
from this branch without the user's later explicit green-head procedure.
