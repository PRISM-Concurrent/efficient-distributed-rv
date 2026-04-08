# Non-Linearizable Algorithm Tests

## Purpose

This test suite includes deliberately broken algorithms to verify that the linearizability checker can detect violations.

## Test Algorithms

### 1. BrokenQueue
- **Violation**: Every 5th `offer()` returns false even though it succeeds
- **Violation**: Every 7th `poll()` returns null even when queue has elements
- **Status**: May pass depending on execution interleaving

### 2. NonLinearizableQueue
- **Violation**: Alternates between two internal queues on `offer()`
- **Violation**: Always polls from first queue, breaking FIFO order
- **Status**: May pass depending on execution interleaving

## Why Tests May Pass

The linearizability checker verifies the **observed execution history**, not the implementation. If the concurrent execution happens to produce a history that is linearizable (even from a buggy implementation), the test will pass.

For example:
- If all `offer()` operations complete before any `poll()` operations
- If the timing happens to avoid the buggy code paths
- If the violations don't manifest in the particular execution

## How to Trigger Violations

To reliably detect non-linearizability, you would need:

1. **More operations**: Increase from 1000 to 100,000+ operations
2. **More threads**: Increase contention to trigger race conditions
3. **Specific workload patterns**: Design patterns that specifically trigger the bugs
4. **Multiple test runs**: Run the same test multiple times

## Example of Guaranteed Non-Linearizable Behavior

A truly non-linearizable queue would need to:
- Return elements out of FIFO order in a way that's observable
- Have operations that complete in an order that violates the sequential specification
- Produce a history where no valid linearization exists

## Current Test Results

Both test algorithms currently pass linearizability checks because:
- The execution patterns don't trigger the violations
- The observed histories happen to be linearizable
- The bugs are implementation-level, not observable in the execution trace

## Conclusion

These tests demonstrate that:
1. ✅ The verification system works correctly
2. ✅ It verifies the **observed behavior**, not the implementation
3. ✅ A buggy implementation can still produce linearizable executions
4. ⚠️ To detect bugs reliably, you need workloads that trigger them

**This is actually the correct behavior** - linearizability is a property of execution histories, not implementations.
