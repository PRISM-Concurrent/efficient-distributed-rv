package phd.distributed.datamodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;

import phd.distributed.api.DistAlgorithm;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

class OperationCallTest {

    private MethodInf mockMethodInf;
    private Method mockMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        mockMethod = String.class.getMethod("length");
        mockMethodInf = new MethodInf(mockMethod);
    }

    @Test
    void testOperationCallCreation() {
        // Given
        Object args = "test argument";

        // When
        OperationCall operationCall = new OperationCall(args, mockMethodInf);

        // Then
        assertEquals(args, operationCall.args());
        assertEquals(mockMethodInf, operationCall.method());
    }

    @Test
    void testOperationCallWithNullArgs() {
        // When
        OperationCall operationCall = new OperationCall(null, mockMethodInf);

        // Then
        assertNull(operationCall.args());
        assertEquals(mockMethodInf, operationCall.method());
    }

    @Test
    void testOperationCallWithArrayArgs() {
        // Given
        Object[] args = {"arg1", "arg2", "arg3"};

        // When
        OperationCall operationCall = new OperationCall(args, mockMethodInf);

        // Then
        assertEquals(args, operationCall.args());
        assertEquals(mockMethodInf, operationCall.method());
    }

    @Test
    void testToStringWithNullArgs() {
        // Given
        OperationCall operationCall = new OperationCall(null, mockMethodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op length(null)", result);
    }

    @Test
    void testToStringWithSingleArg() {
        // Given
        String arg = "test";
        OperationCall operationCall = new OperationCall(arg, mockMethodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op length(test)", result);
    }

    @Test
    void testToStringWithArrayArgs() {
        // Given
        Object[] args = {"arg1", 42, true};
        OperationCall operationCall = new OperationCall(args, mockMethodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op length(arg1, 42, true)", result);
    }

    @Test
    void testToStringWithEmptyArray() {
        // Given
        Object[] args = {};
        OperationCall operationCall = new OperationCall(args, mockMethodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op length()", result);
    }

    @Test
    void testToStringWithSingleElementArray() {
        // Given
        Object[] args = {"single"};
        OperationCall operationCall = new OperationCall(args, mockMethodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op length(single)", result);
    }

    @Test
    void testChooseOpWithNoParameterMethod() throws NoSuchMethodException {
        // Given
        DistAlgorithm mockAlgorithm = mock(DistAlgorithm.class);
        Method noParamMethod = String.class.getMethod("length");
        MethodInf noParamMethodInf = new MethodInf(noParamMethod);

        when(mockAlgorithm.methods()).thenReturn(List.of(noParamMethodInf));

        // When
        OperationCall result = OperationCall.chooseOp(mockAlgorithm, 1);

        // Then
        assertNotNull(result);
        assertEquals(noParamMethodInf, result.method());
        assertNull(result.args());
    }

    @Test
    void testChooseOpWithSingleParameterMethod() throws NoSuchMethodException {
        // Given
        DistAlgorithm mockAlgorithm = mock(DistAlgorithm.class);
        Method singleParamMethod = String.class.getMethod("charAt", int.class);
        MethodInf singleParamMethodInf = new MethodInf(singleParamMethod);

        when(mockAlgorithm.methods()).thenReturn(List.of(singleParamMethodInf));

        // When
        OperationCall result = OperationCall.chooseOp(mockAlgorithm, 5);

        // Then
        assertNotNull(result);
        assertEquals(singleParamMethodInf, result.method());
        assertNotNull(result.args());
        assertEquals(Integer.class, result.args().getClass());
        assertEquals(5, result.args()); // ValueGenerator should return the processId for int
    }

    @Test
    void testChooseOpWithMultipleParameterMethod() throws NoSuchMethodException {
        // Given
        DistAlgorithm mockAlgorithm = mock(DistAlgorithm.class);
        Method multiParamMethod = String.class.getMethod("substring", int.class, int.class);
        MethodInf multiParamMethodInf = new MethodInf(multiParamMethod);

        when(mockAlgorithm.methods()).thenReturn(List.of(multiParamMethodInf));

        // When
        OperationCall result = OperationCall.chooseOp(mockAlgorithm, 3);

        // Then
        assertNotNull(result);
        assertEquals(multiParamMethodInf, result.method());
        assertNotNull(result.args());
        assertTrue(result.args().getClass().isArray());

        Object[] args = (Object[]) result.args();
        assertEquals(2, args.length);
        assertEquals(3, args[0]); // processId + 0
        assertEquals(4, args[1]); // processId + 1
    }

    @Test
    void testChooseOpWithMultipleMethods() throws NoSuchMethodException {
        // Given
        DistAlgorithm mockAlgorithm = mock(DistAlgorithm.class);
        Method method1 = String.class.getMethod("length");
        Method method2 = String.class.getMethod("isEmpty");
        MethodInf methodInf1 = new MethodInf(method1);
        MethodInf methodInf2 = new MethodInf(method2);

        when(mockAlgorithm.methods()).thenReturn(List.of(methodInf1, methodInf2));

        // When - call multiple times to potentially get different methods
        OperationCall result1 = OperationCall.chooseOp(mockAlgorithm, 1);
        OperationCall result2 = OperationCall.chooseOp(mockAlgorithm, 2);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.method() == methodInf1 || result1.method() == methodInf2);
        assertTrue(result2.method() == methodInf1 || result2.method() == methodInf2);
    }

    @Test
    void testChooseOpConsistencyWithSameProcessId() throws NoSuchMethodException {
        // Given
        DistAlgorithm mockAlgorithm = mock(DistAlgorithm.class);
        Method method = String.class.getMethod("charAt", int.class);
        MethodInf methodInf = new MethodInf(method);

        when(mockAlgorithm.methods()).thenReturn(List.of(methodInf));

        // When
        OperationCall result1 = OperationCall.chooseOp(mockAlgorithm, 10);
        OperationCall result2 = OperationCall.chooseOp(mockAlgorithm, 10);

        // Then - should generate same args for same processId
        assertEquals(result1.args(), result2.args());
        assertEquals(result1.method(), result2.method());
    }

    @Test
    void testChooseOpWithEmptyMethodList() {
        // Given
        DistAlgorithm mockAlgorithm = mock(DistAlgorithm.class);
        when(mockAlgorithm.methods()).thenReturn(List.of());

        // When & Then
        assertThrows(IndexOutOfBoundsException.class, () -> {
            OperationCall.chooseOp(mockAlgorithm, 1);
        });
    }

    @Test
    void testToStringWithComplexArgs() throws NoSuchMethodException {
        // Given
        Method method = String.class.getMethod("substring", int.class, int.class);
        MethodInf methodInf = new MethodInf(method);
        Object[] args = {10, 20};
        OperationCall operationCall = new OperationCall(args, methodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op substring(10, 20)", result);
    }

    @Test
    void testToStringWithNullElementInArray() throws NoSuchMethodException {
        // Given
        Method method = String.class.getMethod("substring", int.class, int.class);
        MethodInf methodInf = new MethodInf(method);
        Object[] args = {10, null};
        OperationCall operationCall = new OperationCall(args, methodInf);

        // When
        String result = operationCall.toString();

        // Then
        assertEquals("op substring(10, null)", result);
    }
}
