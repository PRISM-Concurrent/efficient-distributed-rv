# Unit Test Suite for Efficient Distributed RV

This document describes the comprehensive unit test suite created for your distributed runtime verification system.

## Test Coverage

### 1. Data Model Tests

#### `EventTest.java`
- Tests the `Event` class constructor and methods
- Verifies proper handling of null data
- Tests the `toString()` method formatting
- Covers edge cases with complex objects

#### `MethodInfTest.java`
- Tests the `MethodInf` wrapper for Java reflection methods
- Verifies parameter type extraction
- Tests return type handling
- Covers methods with no parameters, single parameters, and multiple parameters

#### `OperationCallTest.java`
- Tests the `OperationCall` class creation and methods
- Verifies the `chooseOp` static method for random operation selection
- Tests `toString()` formatting with different argument types
- Includes mock-based testing for `DistAlgorithm` integration

#### `ValueGeneratorTest.java`
- Comprehensive tests for the value generation system
- Tests primitive types (int, long, double, float, boolean, char, byte, short)
- Tests wrapper classes and String generation
- Tests collection generation (ArrayList, HashSet, LinkedList)
- Tests Map generation (HashMap, ConcurrentHashMap)
- Tests array generation for different types
- Tests enum generation
- Tests custom generator registration
- Tests cache functionality and statistics

### 2. Snapshot Mechanism Tests

#### `LogTest.java`
- Tests the `Log` class for per-thread event storage
- Verifies thread-safe event writing
- Tests event retrieval and ordering
- Covers edge cases like invalid thread IDs

#### `CollectFAIncTest.java`
- Tests the main snapshot algorithm implementation
- Verifies atomic counter functionality
- Tests concurrent access scenarios
- Verifies event ordering by counter values
- Tests the `scanAll()` method for global event ordering

### 3. Core System Tests

#### `WrapperTest.java`
- Tests the `Wrapper` class that executes operations
- Uses mocking to verify interaction with `DistAlgorithm` and `Snapshot`
- Tests exception handling during operation execution
- Verifies proper logging and snapshot capture

#### `VerifierTest.java`
- Tests the `Verifier` class for trace analysis
- Verifies interaction with the snapshot mechanism
- Tests handling of different event set sizes
- Tests exception propagation

#### `ExecutionerTest.java`
- Tests the main orchestrator class
- Verifies thread pool management
- Tests timeout handling
- Tests scalability with different process counts
- Tests system reusability

### 4. API Tests

#### `ATest.java`
- Tests the `A` class (DistAlgorithm implementation)
- Verifies dynamic class loading via reflection
- Tests method filtering (removes dangerous methods)
- Tests operation execution with different argument types
- Verifies exception handling during method invocation

### 5. Integration Tests

#### `SystemIntegrationTest.java`
- End-to-end testing of the complete system
- Tests with different data structures (ConcurrentLinkedQueue, String, etc.)
- Tests system scalability and concurrent execution
- Tests memory usage patterns
- Tests system reproducibility
- Verifies event ordering consistency across the entire system

#### `AppTest.java` (Updated)
- Tests the main application entry point
- Verifies the main method executes without exceptions
- Tests execution time constraints
- Tests multiple executions and thread safety

## Test Features

### Mocking
- Uses Mockito for isolating components during testing
- Enables testing of interactions between components
- Allows testing of error conditions

### Concurrency Testing
- Tests thread safety of core components
- Verifies proper handling of concurrent access
- Tests system behavior under load

### Edge Case Coverage
- Tests with null values
- Tests with empty collections
- Tests with invalid inputs
- Tests exception scenarios

### Performance Testing
- Tests execution time constraints
- Tests memory usage patterns
- Tests scalability with different thread counts

## Running the Tests

### Prerequisites
- Java 21 (as specified in pom.xml)
- Maven 3.6+

### Commands
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=EventTest

# Run tests with verbose output
mvn test -X

# Run integration tests only
mvn test -Dtest="*IntegrationTest"

# Generate test coverage report
mvn jacoco:report
```

### Test Categories

1. **Unit Tests**: Test individual classes in isolation
   - `*Test.java` files in component packages

2. **Integration Tests**: Test component interactions
   - `SystemIntegrationTest.java`

3. **End-to-End Tests**: Test complete system functionality
   - `AppTest.java`

## Test Quality Metrics

- **Line Coverage**: Aims for >80% coverage of testable code
- **Branch Coverage**: Tests both success and failure paths
- **Concurrency Coverage**: Tests thread-safe operations
- **Exception Coverage**: Tests error handling scenarios

## Maintenance

### Adding New Tests
1. Follow the existing naming convention (`*Test.java`)
2. Use appropriate test categories (unit/integration)
3. Include both positive and negative test cases
4. Add concurrency tests for thread-safe components

### Test Data
- Uses `ValueGenerator` for consistent test data generation
- Employs mocking for external dependencies
- Uses deterministic inputs where possible for reproducible tests

## Notes

- Some components (like the actual distributed execution) are difficult to unit test in isolation, so integration tests provide the primary coverage
- The test suite is designed to be fast-running for development feedback
- Longer-running integration tests verify system behavior under realistic conditions
- All tests are designed to be deterministic and not depend on external resources
