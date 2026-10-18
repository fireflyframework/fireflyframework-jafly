# Jafly implementation plan

Goal: migrate Java framework into fireflyframework-jafly with original history, compatible Maven artifacts, one reactor/release train, verified CI/CD and downstream cutover.
Spec: work/design.md. Execution: inline, autonomous per user instruction; independent final review.

Global constraints: 63 source repos, preserve all original Git history/refs and tracked content; JDK25/Maven3.9; original GAs; concrete 26.10.17-SNAPSHOT; no destructive history rewrite or stable publication without verification.
Review focus: BOM/parent consuming application version leakage; overlooked example POMs; cross-repo parent paths; source branches ahead of default; publication success unsupported by artifact inventory.

1. Pin mirror refs and import all source trees into isolated new local repository. Record manifest and verify all blobs/modes before modifying. Preserve namespaced branch/tag refs and original commits. Verify all 63 imports and refs.
2. Write regression checks for reactor completeness, resolved parents, single version, internal dependencies and complete BOM. Observe failure on original sources. Implement root reactor and POM adaptation, parent catalog, version updater and tests. Run fresh Maven validate and full verify with tests; fix root causes.
3. Consolidate CI/release into destination .github/workflows. Verify scripts with tests, syntax/actionlint and a real GitHub CI run. Separate exact publication checks; never soft-pass unknown deploy failure. External consumer tests use empty local repo.
4. Publish destination preserving visibility and all historical refs. Verify remote heads/tags and original commit reachability. Run independent review and resolve material findings with regression evidence.
5. Update active downstream organization/CLI/site/book references and stop source Java duplicate publication through minimal focused updates only after destination CI success. Verify each changed repository using its configured checks and remote CI; preserve metadata/history and release pointers.
6. Export organization inventory, exact mapping and final evidence to outputs and monorepo docs/migration. Explicitly distinguish verified migration, pending external credentials/publication and source metadata retained by links.
