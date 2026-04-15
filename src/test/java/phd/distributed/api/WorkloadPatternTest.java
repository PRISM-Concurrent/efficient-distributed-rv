package phd.distributed.api;

import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadPatternTest {

    @Test
    void testUniformPattern() {
        WorkloadPattern pattern = WorkloadPattern.uniform(100);
        List<Event> events = pattern.generate();

        assertEquals(100, events.size());
        assertEquals(100, pattern.getOperations());
    }

    @Test
    void testUniformPatternWithThreads() {
        WorkloadPattern pattern = WorkloadPattern.uniform(100, 8);
        List<Event> events = pattern.generate();

        assertEquals(100, events.size());
        assertEquals(8, pattern.getThreads());

        // Verify thread IDs are within range
        assertTrue(events.stream().allMatch(e -> e.getId() >= 0 && e.getId() < 8));
    }

    @Test
    void testDeterministicWithSeed() {
        WorkloadPattern pattern1 = WorkloadPattern.withSeed(50, 4, 12345);
        WorkloadPattern pattern2 = WorkloadPattern.withSeed(50, 4, 12345);

        List<Event> events1 = pattern1.generate();
        List<Event> events2 = pattern2.generate();

        // Same seed should produce same events
        assertEquals(events1.size(), events2.size());
        for (int i = 0; i < events1.size(); i++) {
            assertEquals(events1.get(i).getId(), events2.get(i).getId());
        }
    }
}
