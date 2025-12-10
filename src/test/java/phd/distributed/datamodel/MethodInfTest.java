package phd.distributed.datamodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

class MethodInfTest {

    private Method stringLengthMethod;
    private Method stringSubstringMethod;
    private Method objectToStringMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        stringLengthMethod = String.class.getMethod("length");
        stringSubstringMethod = String.class.getMethod("substring", int.class, int.class);
        objectToStringMethod = Object.class.getMethod("toString");
    }

    @Test
    void testMethodInfCreationWithNoParameters() {
        // When
        MethodInf methodInf = new MethodInf(stringLengthMethod);

        // Then
        assertEquals("length", methodInf.getName());
        assertEquals(stringLengthMethod, methodInf.getMethod());
        assertEquals(0, methodInf.getParameterTypes().length);
        assertEquals(int.class, methodInf.getTypeReturn());
        assertTrue(methodInf.getTypeParam().isEmpty());
    }

    @Test
    void testMethodInfCreationWithParameters() {
        // When
        MethodInf methodInf = new MethodInf(stringSubstringMethod);

        // Then
        assertEquals("substring", methodInf.getName());
        assertEquals(stringSubstringMethod, methodInf.getMethod());
        assertEquals(2, methodInf.getParameterTypes().length);
        assertEquals(int.class, methodInf.getParameterTypes()[0]);
        assertEquals(int.class, methodInf.getParameterTypes()[1]);
        assertEquals(String.class, methodInf.getTypeReturn());
        assertEquals(2, methodInf.getTypeParam().size());
    }

    @Test
    void testGetParameterTypes() {
        // Given
        MethodInf methodInf = new MethodInf(stringSubstringMethod);

        // When
        Class<?>[] paramTypes = methodInf.getParameterTypes();

        // Then
        assertNotNull(paramTypes);
        assertEquals(2, paramTypes.length);
        assertEquals(int.class, paramTypes[0]);
        assertEquals(int.class, paramTypes[1]);
    }

    @Test
    void testGetTypeParam() {
        // Given
        MethodInf methodInf = new MethodInf(stringSubstringMethod);

        // When
        List<Type> typeParams = methodInf.getTypeParam();

        // Then
        assertNotNull(typeParams);
        assertEquals(2, typeParams.size());
        assertEquals(int.class, typeParams.get(0));
        assertEquals(int.class, typeParams.get(1));
    }

    @Test
    void testGetTypeReturn() {
        // Given
        MethodInf lengthMethodInf = new MethodInf(stringLengthMethod);
        MethodInf substringMethodInf = new MethodInf(stringSubstringMethod);

        // Then
        assertEquals(int.class, lengthMethodInf.getTypeReturn());
        assertEquals(String.class, substringMethodInf.getTypeReturn());
    }

    @Test
    void testToString() {
        // Given
        MethodInf methodInf = new MethodInf(stringSubstringMethod);

        // When
        String result = methodInf.toString();

        // Then
        assertTrue(result.contains("Method: substring"));
        assertTrue(result.contains("Args: [int, int]"));
        assertTrue(result.contains("Return: class java.lang.String"));
    }

    @Test
    void testToStringWithNoParameters() {
        // Given
        MethodInf methodInf = new MethodInf(stringLengthMethod);

        // When
        String result = methodInf.toString();

        // Then
        assertTrue(result.contains("Method: length"));
        assertTrue(result.contains("Args: []"));
        assertTrue(result.contains("Return: int"));
    }

    @Test
    void testMethodInfWithObjectMethod() {
        // When
        MethodInf methodInf = new MethodInf(objectToStringMethod);

        // Then
        assertEquals("toString", methodInf.getName());
        assertEquals(objectToStringMethod, methodInf.getMethod());
        assertEquals(0, methodInf.getParameterTypes().length);
        assertEquals(String.class, methodInf.getTypeReturn());
    }
}
