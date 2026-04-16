# Contributing

## Reporting Issues

Use GitHub Issues for bugs, unexpected behavior, or questions about
extending the framework.

## Development Setup

Same as INSTALL.md. Run the test suite to verify your environment:

    mvn test
    # Expected: Tests run: 78, Failures: 0, Errors: 0

## Adding a New Data Structure

See USER_MANUAL.md Section 4 for a complete walkthrough covering:
- Sequential specification in Clojure
- Registration in typelin.clj
- Registration in AlgorithmLibrary
- WorkloadPattern classification

## Adding a New Snapshot Strategy

1. Extend `phd.distributed.snapshot.Snapshot`
2. Implement `write(int tid, Object inv)`,
   `snapshot(int tid, Object res)`, and `buildXE()`
3. Pass the instance to `new Executioner(threads, ops, alg, type, mySnapshot)`

## Code Style

**Java:** Standard Java conventions. No external formatter enforced.
**Clojure:** Standard Clojure style. Namespace per data structure in
`src/main/clojure/spec/`.

## Running the Tests

    mvn test                              # all tests
    mvn test -Dtest=AlgorithmLibraryTest  # specific class

## Submitting Changes

Open a pull request against `docs/product`. Include a description of
the change and, if adding a new data structure, the sequential
specification and a brief correctness argument.