package phd.distributed.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import phd.distributed.api.DistAlgorithm;
import phd.distributed.datamodel.MethodInf;
import phd.distributed.snapshot.Snapshot;
import java.lang.reflect.Method;
import java.util.List;

class WrapperTest {

    private DistAlgorithm mockAlgorithm;
    private Snapshot mockSnapshot;
    private Wrapper wrapper;
    private MethodInf mockMethodInf;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        mockAlgorithm = mock(DistAlgorithm.class);
        mockSnapshot = mock(Snapshot.class);
        wrapper = new Wrapper(mockAlgorithm, mockSnapshot);

        Method testMethod = String.class.getMethod("length");
        mockMethodInf = new MethodInf(testMethod);
    }

    @Test
    void testWrapperCreation() {
        // Then
        assertNotNull(wrapper);
    }

    @Test
    void testExecuteCallsSnapshotWrite() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any())).thenReturn(5);

        // When
        wrapper.execute(1);

        // Then
        verify(mockSnapshot, times(1)).write(eq(1), anyString());
    }

    @Test
    void testExecuteCallsAlgorithmApply() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any())).thenReturn(5);

        // When
        wrapper.execute(1);

        // Then
        verify(mockAlgorithm, times(1)).apply(any(MethodInf.class), any());
    }

    @Test
    void testExecuteCallsSnapshotSnapshot() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any())).thenReturn(5);

        // When
        wrapper.execute(1);

        // Then
        verify(mockSnapshot, times(1)).snapshot(eq(1), eq(5));
    }

    @Test
    void testExecuteHandlesExceptionGracefully() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any())).thenThrow(new RuntimeException("Test exception"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> wrapper.execute(1));

        // Verify that write and snapshot are still called
        verify(mockSnapshot, times(1)).write(eq(1), anyString());
        verify(mockSnapshot, times(1)).snapshot(eq(1), isNull());
    }

    @Test
    void testExecuteWithDifferentProcessIds() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any())).thenReturn("result");

        // When
        wrapper.execute(0);
        wrapper.execute(5);
        wrapper.execute(10);

        // Then
        verify(mockSnapshot, times(1)).write(eq(0), anyString());
        verify(mockSnapshot, times(1)).write(eq(5), anyString());
        verify(mockSnapshot, times(1)).write(eq(10), anyString());

        verify(mockSnapshot, times(1)).snapshot(eq(0), eq("result"));
        verify(mockSnapshot, times(1)).snapshot(eq(5), eq("result"));
        verify(mockSnapshot, times(1)).snapshot(eq(10), eq("result"));
    }

    @Test
    void testExecuteWithNullResult() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any())).thenReturn(null);

        // When
        wrapper.execute(2);

        // Then
        verify(mockSnapshot, times(1)).snapshot(eq(2), isNull());
    }

    @Test
    void testExecuteSequentialCalls() {
        // Given
        when(mockAlgorithm.methods()).thenReturn(List.of(mockMethodInf));
        when(mockAlgorithm.apply(any(), any()))
            .thenReturn("result1")
            .thenReturn("result2")
            .thenReturn("result3");

        // When
        wrapper.execute(1);
        wrapper.execute(1);
        wrapper.execute(1);

        // Then
        verify(mockSnapshot, times(3)).write(eq(1), anyString());
        verify(mockSnapshot, times(1)).snapshot(eq(1), eq("result1"));
        verify(mockSnapshot, times(1)).snapshot(eq(1), eq("result2"));
        verify(mockSnapshot, times(1)).snapshot(eq(1), eq("result3"));
    }

    @Test
    void testExecuteWithMultipleMethods() throws NoSuchMethodException {
        // Given
        Method method1 = String.class.getMethod("length");
        Method method2 = String.class.getMethod("isEmpty");
        MethodInf methodInf1 = new MethodInf(method1);
        MethodInf methodInf2 = new MethodInf(method2);

        when(mockAlgorithm.methods()).thenReturn(List.of(methodInf1, methodInf2));
        when(mockAlgorithm.apply(any(), any())).thenReturn(42);

        // When
        wrapper.execute(3);

        // Then
        verify(mockAlgorithm, times(1)).methods();
        verify(mockAlgorithm, times(1)).apply(any(MethodInf.class), any());
        verify(mockSnapshot, times(1)).write(eq(3), anyString());
        verify(mockSnapshot, times(1)).snapshot(eq(3), eq(42));
    }
}
