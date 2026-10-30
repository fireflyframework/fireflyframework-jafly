# HTTP Diagnostic Body Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan inline. The approved parent coordinates fresh independent review after source freeze.

**Goal:** Make explicit body logging a bounded, zero-buffer-ownership observer of HTTP streams.

**Architecture:** A private per-subscription prefix capture copies bytes without changing buffers. Request and response decorators pass original signals and demand; flush groups remain nested. The actual transport and consumers retain ownership.

**Tech Stack:** Existing SLF4J/Logback, Spring Boot 4.1.1, Spring WebFlux, Reactor and pooled Netty buffers; original retained20-reactor dependency inputs. No new dependency.

**Spec:** `docs/superpowers/specs/2026-10-30-http-logging-streaming-design.md`

## Global Constraints

- Base public20c; preserve previous frozen source/proofs and Config r2; no commits/pushes/H edits.
- No body/header/query default changes, public API/clock changes, new logging schema or enterprise policy in this block.
- No business database/cache/role changes; no Maven/native HTTP/DB execution.
- Source compilers/JVMs deny network and limit writes to new private output; original1095 class/JAR inputs and5 tools remain exact.

## Review Focus

- Endless/incomplete bodies must deliver the first frame and preserve cancellation.
- Pooled data must be released only by its existing consumer, including error/cancel paths.
- Repeated subscriptions and nested flush groups must not share or reset the wrong prefix budget.
- Fragmented UTF8 and zero/truncated caps must not change business bytes or buffer positions.
- Diagnostics that fail must not replace business success/error/cancel signals.

## Task 1: Genuine regression RED

**Files:** Create `modules/web/src/test/java/org/fireflyframework/web/logging/filter/HttpRequestLoggingStreamingTest.java`.

- [x] Add actual pooled-buffer first-frame/demand/error/cancel tests with an explicit draining/releasing mock response; preserve body-off controls.
- [x] Add repeated-subscription, fragmented UTF8, writeAndFlushWith group/cap and explicit flag/payload controls.
- [x] Compile/run against unchanged20c implementation using original retained dependency graph; record actual assertion failures, no DB/server.

## Task 2: Transparent bounded capture

**Files:** Modify `modules/web/src/main/java/org/fireflyframework/web/logging/filter/HttpRequestLoggingWebFilter.java`.

**Interfaces:** Preserve existing public constructor/WebFilter API. Private prefix capture accepts a borrowed DataBuffer and completes/abandons diagnostic bytes. The obsolete aggregate-list extraction helper is replaced by the same non-consuming absolute-byte copy inside the capture.

- [x] Replace request and writeWith collectList/join with per-subscription bounded copy on onNext and one diagnostic completion callback.
- [x] Override writeAndFlushWith with one capture per subscription, preserving original nested group boundaries.
- [x] Remove observer ownership/aggregation; ensure ordinary diagnostic failures cannot interrupt traffic.
- [x] Run new controls plus all23 previous maintained controls; all must pass without source dependency drift.

## Task 3: Documentation and source freeze

**Files:** Update `docs/site/modules/web.md`; create external source qualification/freeze evidence.

- [x] Document prefix-only memory, unchanged demand/bytes/ownership, terminal behavior and separate enterprise logging policy boundary.
- [x] Run full affected source controls and verify all original input/source pins before/after.
- [x] Restore and verify complete source archive, exact20c delta and original dependency/tool graph.
- [x] Freeze source/manifest and author result for Root/fresh peer review. No publication or runtime approval is created.
