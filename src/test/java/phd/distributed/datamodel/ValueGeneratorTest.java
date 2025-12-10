package phd.distributed.datamodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ValueGeneratorTest {

    @BeforeEach
    void setUp() {
        ValueGenerator.clearCache();
    }

    @AfterEach
    void tearDown() {
        ValueGenerator.clearCache();
    }

    @Test
    void testPrimitiveIntGeneration() {
        // When
        Object result = ValueGenerator.getValue(int.class, 42);

        // Then
        assertNotNull(result);
        assertEquals(Integer.class, result.getClass());
        assertEquals(42, result);
    }

    @Test
    void testPrimitiveWrapperIntegerGeneration() {
        // When
        Object result = ValueGenerator.getValue(Integer.class, 100);

        // Then
        assertNotNull(result);
        assertEquals(Integer.class, result.getClass());
        assertEquals(100, result);
    }

    @Test
    void testPrimitiveLongGeneration() {
        // When
        Object result = ValueGenerator.getValue(long.class, 123);

        // Then
        assertNotNull(result);
        assertEquals(Long.class, result.getClass());
        assertEquals(123L, result);
    }

    @Test
    void testPrimitiveDoubleGeneration() {
        // When
        Object result = ValueGenerator.getValue(double.class, 10);

        // Then
        assertNotNull(result);
        assertEquals(Double.class, result.getClass());
        assertEquals(10.5, result);
    }

    @Test
    void testPrimitiveFloatGeneration() {
        // When
        Object result = ValueGenerator.getValue(float.class, 5);

        // Then
        assertNotNull(result);
        assertEquals(Float.class, result.getClass());
        assertEquals(5.25f, result);
    }

    @Test
    void testPrimitiveBooleanGeneration() {
        // When
        Object evenResult = ValueGenerator.getValue(boolean.class, 2);
        Object oddResult = ValueGenerator.getValue(boolean.class, 3);

        // Then
        assertNotNull(evenResult);
        assertNotNull(oddResult);
        assertEquals(Boolean.class, evenResult.getClass());
        assertEquals(Boolean.class, oddResult.getClass());
        assertTrue((Boolean) evenResult);  // even number -> true
        assertFalse((Boolean) oddResult);  // odd number -> false
    }

    @Test
    void testPrimitiveCharGeneration() {
        // When
        Object result = ValueGenerator.getValue(char.class, 0);

        // Then
        assertNotNull(result);
        assertEquals(Character.class, result.getClass());
        assertEquals('A', result);
    }

    @Test
    void testPrimitiveByteGeneration() {
        // When
        Object result = ValueGenerator.getValue(byte.class, 200);

        // Then
        assertNotNull(result);
        assertEquals(Byte.class, result.getClass());
        assertEquals((byte) (200 % 128), result);
    }

    @Test
    void testStringGeneration() {
        // When
        Object result = ValueGenerator.getValue(String.class, 42);

        // Then
        assertNotNull(result);
        assertEquals(String.class, result.getClass());
        assertEquals("msg-42", result);
    }

    @Test
    void testObjectGeneration() {
        // When
        Object result = ValueGenerator.getValue(Object.class, 15);

        // Then
        assertNotNull(result);
        assertEquals(String.class, result.getClass());
        assertEquals("obj-15", result);
    }

    @Test
    void testArrayListGeneration() {
        // When
        Object result = ValueGenerator.getValue(ArrayList.class, 5);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof ArrayList);
        ArrayList<?> list = (ArrayList<?>) result;
        assertEquals(2, list.size());
        assertEquals("elem-5", list.get(0));
        assertEquals("elem-6", list.get(1));
    }

    @Test
    void testHashSetGeneration() {
        // When
        Object result = ValueGenerator.getValue(HashSet.class, 10);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof HashSet);
        HashSet<?> set = (HashSet<?>) result;
        assertTrue(set.size() >= 1); // Sets may deduplicate
        assertTrue(set.contains("elem-10"));
    }

    @Test
    void testHashMapGeneration() {
        // When
        Object result = ValueGenerator.getValue(HashMap.class, 7);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof HashMap);
        HashMap<?, ?> map = (HashMap<?, ?>) result;
        assertEquals(1, map.size());
        assertEquals("value-7", map.get("key-7"));
    }

    @Test
    void testIntArrayGeneration() {
        // When
        Object result = ValueGenerator.getValue(int[].class, 3);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof int[]);
        int[] array = (int[]) result;
        assertEquals(2, array.length);
        assertEquals(3, array[0]);
        assertEquals(4, array[1]);
    }

    @Test
    void testStringArrayGeneration() {
        // When
        Object result = ValueGenerator.getValue(String[].class, 1);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof String[]);
        String[] array = (String[]) result;
        assertEquals(2, array.length);
        assertEquals("msg-1", array[0]);
        assertEquals("msg-2", array[1]);
    }

    @Test
    void testCustomGeneratorRegistration() {
        // Given
        ValueGenerator.registerGenerator(String.class, base -> "custom-" + base);

        // When
        Object result = ValueGenerator.getValue(String.class, 99);

        // Then
        assertEquals("custom-99", result);
    }

    @Test
    void testNullTypeThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ValueGenerator.getValue(null, 1);
        });
    }

    @Test
    void testCacheStats() {
        // Given
        ValueGenerator.getValue(int.class, 1);
        ValueGenerator.getValue(String.class, 2);

        // When
        Map<String, Integer> stats = ValueGenerator.getCacheStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.containsKey("cacheSize"));
        assertTrue(stats.containsKey("customGenerators"));
        assertTrue(stats.get("cacheSize") >= 2);
    }

    @Test
    void testClearCache() {
        // Given
        ValueGenerator.getValue(int.class, 1);
        ValueGenerator.registerGenerator(String.class, base -> "test");

        // When
        ValueGenerator.clearCache();
        Map<String, Integer> stats = ValueGenerator.getCacheStats();

        // Then
        assertEquals(0, stats.get("customGenerators"));
    }

    @Test
    void testConsistentGeneration() {
        // When
        Object result1 = ValueGenerator.getValue(int.class, 42);
        Object result2 = ValueGenerator.getValue(int.class, 42);

        // Then
        assertEquals(result1, result2);
    }

    @Test
    void testDifferentBasesProduceDifferentValues() {
        // When
        Object result1 = ValueGenerator.getValue(String.class, 1);
        Object result2 = ValueGenerator.getValue(String.class, 2);

        // Then
        assertNotEquals(result1, result2);
        assertEquals("msg-1", result1);
        assertEquals("msg-2", result2);
    }

    @Test
    void testComplexObjectGeneration() {
        // When - StringBuilder has a no-arg constructor
        Object result = ValueGenerator.getValue(StringBuilder.class, 5);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof StringBuilder);
    }

    @Test
    void testConcurrentHashMapGeneration() {
        // When
        Object result = ValueGenerator.getValue(ConcurrentHashMap.class, 8);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof ConcurrentHashMap);
        ConcurrentHashMap<?, ?> map = (ConcurrentHashMap<?, ?>) result;
        assertEquals(1, map.size());
        assertEquals("value-8", map.get("key-8"));
    }

    @Test
    void testLinkedListGeneration() {
        // When
        Object result = ValueGenerator.getValue(LinkedList.class, 12);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof LinkedList);
        LinkedList<?> list = (LinkedList<?>) result;
        assertEquals(2, list.size());
        assertEquals("elem-12", list.get(0));
        assertEquals("elem-13", list.get(1));
    }

    @Test
    void testCharacterWrapperGeneration() {
        // When
        Object result = ValueGenerator.getValue(Character.class, 25);

        // Then
        assertNotNull(result);
        assertEquals(Character.class, result.getClass());
        assertEquals((char) ('A' + (25 % 26)), result);
    }

    @Test
    void testShortGeneration() {
        // When
        Object result = ValueGenerator.getValue(short.class, 1000);

        // Then
        assertNotNull(result);
        assertEquals(Short.class, result.getClass());
        assertEquals((short) 1000, result);
    }

    // Test for enum generation (using a built-in enum)
    enum TestEnum {
        VALUE1, VALUE2, VALUE3
    }

    @Test
    void testEnumGeneration() {
        // When
        Object result1 = ValueGenerator.getValue(TestEnum.class, 0);
        Object result2 = ValueGenerator.getValue(TestEnum.class, 1);
        Object result3 = ValueGenerator.getValue(TestEnum.class, 3); // Should wrap around

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
        assertTrue(result1 instanceof TestEnum);
        assertEquals(TestEnum.VALUE1, result1);
        assertEquals(TestEnum.VALUE2, result2);
        assertEquals(TestEnum.VALUE1, result3); // 3 % 3 = 0
    }
}
