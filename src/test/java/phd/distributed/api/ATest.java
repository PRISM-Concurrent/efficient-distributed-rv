package phd.distributed.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import phd.distributed.datamodel.MethodInf;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

class ATest {

    private A algorithmWithQueue;
    private A algorithmWithString;

    @BeforeEach
    void setUp() {
        algorithmWithQueue = new A("java.util.concurrent.ConcurrentLinkedQueue");
        algorithmWithString = new A("java.lang.String");
    }

    @Test
    void testConstructorWithValidClass() {
        // When
        A algorithm = new A("java.util.ArrayList");

        // Then
        assertNotNull(algorithm);
        assertNotNull(algorithm.methods());
        assertFalse(algorithm.methods().isEmpty());
    }

    @Test
    void testConstructorWithInvalidClass() {
        // When
        A algorithm = new A("non.existent.Class");

        // Then
        assertNotNull(algorithm);
        assertTrue(algorithm.methods().isEmpty());
    }

    @Test
    void testMethodsReturnsNonEmptyList() {
        // When
        List<MethodInf> methods = algorithmWithQueue.methods();

        // Then
        assertNotNull(methods);
        assertFalse(methods.isEmpty());
    }

    @Test
    void testMethodsFiltersObjectMethods() {
        // Given
        List<MethodInf> methods = algorithmWithString.methods();

        // Then
        // Should not contain Object methods like equals, hashCode, toString, etc.
        boolean containsObjectMethods = methods.stream()
            .anyMatch(m -> m.getName().equals("equals") ||
                          m.getName().equals("hashCode") ||
                          m.getName().equals("getClass"));
        assertFalse(containsObjectMethods);
    }

    @Test
    void testMethodsFiltersDangerousMethods() {
        // Given
        List<MethodInf> methods = algorithmWithString.methods();

        // Then
        // Should not contain dangerous methods
        boolean containsDangerousMethods = methods.stream()
            .anyMatch(m -> m.getName().equals("wait") ||
                          m.getName().equals("notify") ||
                          m.getName().equals("notifyAll"));
        assertFalse(containsDangerousMethods);
    }

    @Test
    void testMethodsFiltersStreamMethods() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();

        // Then
        // Should not contain stream-related methods
        boolean containsStreamMethods = methods.stream()
            .anyMatch(m -> m.getName().equals("parallelStream") ||
                          m.getName().equals("spliterator"));
        assertFalse(containsStreamMethods);
    }

    @Test
    void testApplyWithNoArgsMethod() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();
        MethodInf sizeMethod = methods.stream()
            .filter(m -> m.getName().equals("size"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("size method not found"));

        // When
        Object result = algorithmWithQueue.apply(sizeMethod);

        // Then
        assertNotNull(result);
        assertEquals(Integer.class, result.getClass());
        assertEquals(0, result); // Empty queue should have size 0
    }

    @Test
    void testApplyWithSingleArgMethod() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();
        MethodInf offerMethod = methods.stream()
            .filter(m -> m.getName().equals("offer"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("offer method not found"));

        // When
        Object result = algorithmWithQueue.apply(offerMethod, "test element");

        // Then
        assertNotNull(result);
        assertEquals(Boolean.class, result.getClass());
        assertTrue((Boolean) result);
    }

    @Test
    void testApplyWithMultipleArgs() {
        // Given - Using String.substring(int, int) as an example
        List<MethodInf> methods = algorithmWithString.methods();
        MethodInf substringMethod = methods.stream()
            .filter(m -> m.getName().equals("substring") &&
                        m.getParameterTypes().length == 2)
            .findFirst()
            .orElseThrow(() -> new AssertionError("substring(int, int) method not found"));

        // When
        Object result = algorithmWithString.apply(substringMethod, 0, 3);

        // Then
        assertNotNull(result);
        assertEquals(String.class, result.getClass());
        // The string instance should be empty initially, so substring should work
    }

    @Test
    void testApplyWithNullArgs() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();
        MethodInf sizeMethod = methods.stream()
            .filter(m -> m.getName().equals("size"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("size method not found"));

        // When
        Object result = algorithmWithQueue.apply(sizeMethod, (Object[]) null);

        // Then
        assertNotNull(result);
        assertEquals(0, result);
    }

    @Test
    void testApplyWithEmptyArgs() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();
        MethodInf sizeMethod = methods.stream()
            .filter(m -> m.getName().equals("size"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("size method not found"));

        // When
        Object result = algorithmWithQueue.apply(sizeMethod, new Object[0]);

        // Then
        assertNotNull(result);
        assertEquals(0, result);
    }

    @Test
    void testApplySequentialOperations() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();
        MethodInf offerMethod = methods.stream()
            .filter(m -> m.getName().equals("offer"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("offer method not found"));
        MethodInf sizeMethod = methods.stream()
            .filter(m -> m.getName().equals("size"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("size method not found"));

        // When
        Object offerResult = algorithmWithQueue.apply(offerMethod, "element1");
        Object sizeResult1 = algorithmWithQueue.apply(sizeMethod);
        Object offerResult2 = algorithmWithQueue.apply(offerMethod, "element2");
        Object sizeResult2 = algorithmWithQueue.apply(sizeMethod);

        // Then
        assertTrue((Boolean) offerResult);
        assertEquals(1, sizeResult1);
        assertTrue((Boolean) offerResult2);
        assertEquals(2, sizeResult2);
    }

    @Test
    void testMethodsListIsImmutable() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();
        int originalSize = methods.size();

        // When & Then
        assertThrows(UnsupportedOperationException.class, () -> {
            methods.add(null);
        });
        assertEquals(originalSize, methods.size());
    }

    @Test
    void testConstructorWithAbstractClass() {
        // When
        A algorithm = new A("java.util.AbstractList");

        // Then
        // Should handle the instantiation failure gracefully
        assertNotNull(algorithm);
        assertTrue(algorithm.methods().isEmpty());
    }

    @Test
    void testConstructorWithInterface() {
        // When
        A algorithm = new A("java.util.List");

        // Then
        // Should handle the instantiation failure gracefully
        assertNotNull(algorithm);
        assertTrue(algorithm.methods().isEmpty());
    }

    @Test
    void testApplyWithInvalidMethod() {
        // Given
        A emptyAlgorithm = new A("non.existent.Class");

        // When & Then
        // This should not throw an exception but return null
        // since the instance is null for invalid classes
        assertDoesNotThrow(() -> {
            Object result = emptyAlgorithm.apply(null);
            // Result behavior depends on implementation when instance is null
        });
    }

    @Test
    void testMethodsContainsExpectedQueueMethods() {
        // Given
        List<MethodInf> methods = algorithmWithQueue.methods();

        // When
        List<String> methodNames = methods.stream()
            .map(MethodInf::getName)
            .toList();

        // Then
        assertTrue(methodNames.contains("offer"));
        assertTrue(methodNames.contains("poll"));
        assertTrue(methodNames.contains("size"));
        assertTrue(methodNames.contains("isEmpty"));
    }

    @Test
    void testMethodsContainsExpectedStringMethods() {
        // Given
        List<MethodInf> methods = algorithmWithString.methods();

        // When
        List<String> methodNames = methods.stream()
            .map(MethodInf::getName)
            .toList();

        // Then
        assertTrue(methodNames.contains("length"));
        assertTrue(methodNames.contains("isEmpty"));
        assertTrue(methodNames.contains("charAt"));
        assertTrue(methodNames.contains("substring"));
    }

    @Test
    void testApplyHandlesExceptions() {
        // Given - try to call charAt with invalid index
        List<MethodInf> methods = algorithmWithString.methods();
        MethodInf charAtMethod = methods.stream()
            .filter(m -> m.getName().equals("charAt"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("charAt method not found"));

        // When - this should cause an exception but be handled gracefully
        Object result = algorithmWithString.apply(charAtMethod, 100);

        // Then - should return null when exception occurs
        assertNull(result);
    }
}
