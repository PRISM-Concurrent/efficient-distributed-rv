package phd.distributed.datamodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTest {

    @Test
    void testEventCreation() {
        // Given
        int id = 1;
        String eventData = "test operation";
        int counter = 5;

        // When
        Event event = new Event(id, eventData, counter);

        // Then
        assertEquals(id, event.getId());
        assertEquals(eventData, event.getEvent());
        assertEquals(counter, event.getCounter());
    }

    @Test
    void testEventWithNullData() {
        // Given
        int id = 2;
        Object eventData = null;
        int counter = 10;

        // When
        Event event = new Event(id, eventData, counter);

        // Then
        assertEquals(id, event.getId());
        assertNull(event.getEvent());
        assertEquals(counter, event.getCounter());
    }

    @Test
    void testEventToString() {
        // Given
        int id = 3;
        String eventData = "add(5)";
        int counter = 15;
        Event event = new Event(id, eventData, counter);

        // When
        String result = event.toString();

        // Then
        assertEquals("T3: add(5) [15]", result);
    }

    @Test
    void testEventToStringWithNullData() {
        // Given
        int id = 4;
        Object eventData = null;
        int counter = 20;
        Event event = new Event(id, eventData, counter);

        // When
        String result = event.toString();

        // Then
        assertEquals("T4: null [20]", result);
    }

    @Test
    void testEventWithComplexObject() {
        // Given
        int id = 5;
        Object eventData = new StringBuilder("complex object");
        int counter = 25;

        // When
        Event event = new Event(id, eventData, counter);

        // Then
        assertEquals(id, event.getId());
        assertEquals(eventData, event.getEvent());
        assertEquals(counter, event.getCounter());
        assertTrue(event.toString().contains("complex object"));
    }
}
