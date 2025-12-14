# Removed / Deferred Components (Not Connected in Current Release)

This release focuses on the core distributed instrumentation + verification pipeline (Wrapper / Snapshot-Collect / Verifier / JITLin integration).
To keep the delivery minimal and consistent, we removed several prototype or planned components that were not wired into the current execution path.

The removed code was intended as future performance and product features (scalability, caching, streaming verification), but it was not yet connected to the real linearizability checker nor to the event-to-history conversion used by the current verifier.

Why remove them?
	•	They were not reachable from the current API / demos (dead code).
	•	They provided only placeholder validation (e.g., checking non-null events), not linearizability.
	•	They introduced extra dependencies and complexity (ForkJoinPool, Caffeine, Reactor) without being part of the current “correctness story”.

⸻

1) AdvancedPruningStrategies (Pruning)

What it was:
A set of heuristics to reduce large event logs before verification (keep execution sizes manageable).

What Miguel likely planned:
	•	Add pruning as an optional stage in the verification pipeline:

Wrapper/Collect → X_E events → (optional pruning) → JITLin checker

Why it’s removed now:
	•	Pruning is a heuristic: it can drop events needed to expose a real violation.
	•	In the current release, you want the verifier to operate on the actual constructed history, aligned with your “no false negatives” narrative (or, at least, without introducing extra heuristics that weaken that message).

Summary of strategies (removed):
	•	DependencyAwarePruning: keep first/last per thread + unique ops in the middle.
	•	SamplingPruning: keep 1 of every k events (aggressive).
	•	AdaptivePruning: switch strategies depending on log size.

⸻

2) ParallelVerifier (Parallel Verification Prototype)

What it was:
A prototype verifier that partitions events by Event::getId and checks each partition in parallel.

Important limitation (why it was never correct for linearizability):
	•	Linearizability is not compositional per thread partition.
	•	Grouping by thread (getId) loses all cross-thread interactions, which are exactly where linearizability violations live.

What Miguel likely planned:
	•	Use parallelism for:
	1.	pre-processing (event conversion, building constraints, extracting views), and/or
	2.	running multiple independent checks (e.g., different seeds, workloads, or specifications), not splitting one history into per-thread “subproblems”.

Why it’s removed now:
	•	It currently implements only sanity checks (e != null, id >= 0), not linearizability.
	•	Keeping it could confuse readers/users into thinking verification is actually parallelized safely.

⸻

3) VerificationCache (Result caching prototype)

What it was:
A Caffeine-based cache keyed by events.hashCode() to reuse verification results.

What Miguel likely planned:
	•	Cache results for repeated runs (same workload, same seed, same schedule), e.g.:
	•	VerificationFramework.run() is called many times in benchmarks,
	•	and you avoid re-verifying identical histories.

Why it’s removed now:
	•	The cache key is too weak (events.hashCode()), and Event hash behavior may not be stable across runs.
	•	Caching correctness is subtle: you need a canonical serialization of the history (including order, invocation/return pairing, method args/res, etc.) to avoid collisions.
	•	Adds dependency + complexity without current integration.

If reintroduced later (recommended design):
	•	Key should be a stable digest like SHA-256 over a canonical representation of X_E (not Java object hash).

⸻

4) ReactiveVerifier (Reactive / streaming verification prototype)

What it was:
A Reactor-based pipeline that combines:
	•	cache lookup,
	•	pruning,
	•	parallel validation over events,
	•	timeout/retry policies.

What Miguel likely planned:
	•	Product-oriented mode for “always-on” monitoring:
	•	consume events as a stream,
	•	verify periodically or per window,
	•	enforce timeouts (fail-fast),
	•	retry on transient issues,
	•	record metrics.

Why it’s removed now:
	•	It does not connect to the actual checker (JITLin / sequential spec). It validates only e != null && id >= 0.
	•	Adds Reactor dependency and a “reactive” API surface not used by current demos.
	•	Harder to explain in the current academic/software-track narrative.

⸻

Planned integration (how it would fit later)

If these components come back in a future milestone, the intended architecture would be:
	1.	Instrumentation layer obtains the execution:
	•	Wrapper.execute(...) records invocations and responses
	•	Snapshot / Collect provides communication for the verifier
	2.	Verifier builds the well-formed history:
	•	constructs X_E and the views W_E (as in the current framework)
	3.	Optional performance/product layer (future):
	•	PruningStrategy reduces X_E (explicitly labeled as heuristic)
	•	VerificationCache caches results using a canonical key
	•	ParallelVerifier parallelizes safe sub-tasks (preprocessing or multi-run)
	•	ReactiveVerifier wraps the pipeline for streaming, timeouts, retries
	4.	Decision procedure:
	•	JITLin checker (or any decision algorithm) consumes the final history

⸻

Notes for the current release
	•	The current release prioritizes:
	•	correctness and clarity of the instrumentation path,
	•	direct connection to the real decision procedure,
	•	minimal dependencies and minimal “prototype features”.

These removed classes are kept as design sketches for future productization, once the full verification pipeline and the event-to-history conversion are stable and fully connected.