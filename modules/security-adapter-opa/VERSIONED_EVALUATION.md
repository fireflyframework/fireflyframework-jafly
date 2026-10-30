# Optional versioned policy evaluation

`PolicyDecisionPort`, `Decision`, and the existing boolean OPA adapter retain their existing contracts. The separate `PolicyEvaluationPort` and explicitly constructed `OpaPolicyEvaluationAdapter` support consumers that require a decision and server-reported provenance from one response. Nothing activates this capability automatically.

The configured OPA data document must return an object containing exactly a literal boolean `allow` and a `policy_revision` string, while OPA decision logging must supply the response-level `decision_id`. Additional result fields, including obligations and constraints, are unsupported and make the evaluation indeterminate with empty provenance. They are never silently dropped or treated as fulfilled. Legitimate response-level OPA metadata, such as metrics, explanations and provenance, remains accepted and does not replace the required result revision. For example, the response shape is:

```json
{"result":{"allow":true,"policy_revision":"engine-reported-revision"},"decision_id":"engine-reported-decision-id"}
```

These example identifiers are schema examples. A deployment must project its genuine evaluated policy revision into that result. The adapter retains what the engine response reports; it cannot attest that revision's source, native authorization, cryptographic authenticity, or a current owner authority. Standard boolean responses do not supply this capability and are refused by the structured adapter. A missing decision identifier, including when decision logging is disabled, is indeterminate. Callers requiring provenance must also reject an empty `PolicyEvaluation.provenance()` from any implementation of the port.

Responses are limited to 16 KiB, with duplicate JSON keys, trailing JSON, absent or malformed metadata, control characters, and overlong values refused. Policy revision and decision ID are opaque, retained exactly, and bounded to 1024 and 256 UTF-16 code units respectively. A structured false decision retains its provenance and denies. Transport/HTTP/protocol failures return a stable sanitized indeterminate result with empty provenance. Cancellation emits no evaluation, and partial response buffers are released. No separate metadata fetch, boolean fallback, synthesized revision, clock, grant or cached earlier decision supplies provenance.

The caller owns its original request budget and must apply it to subscription and completion. The adapter does not issue or renew a native grant or deadline. Source checks use actual WebClient request encoding and response buffers with a controlled exchange transport. They are not a running OPA integration or native/business acceptance.
