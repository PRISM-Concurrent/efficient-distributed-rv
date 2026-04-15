package phd.distributed.verifier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;
import static phd.distributed.verifier.AdvancedPruningStrategies.AdaptivePruning;
import static phd.distributed.verifier.AdvancedPruningStrategies.DependencyAwarePruning;
import static phd.distributed.verifier.AdvancedPruningStrategies.SamplingPruning;

import java.util.ArrayList;
import java.util.List;

@Tag("unit")
@Tag("fast")
class PruningComparisonTest {

    @Test
    void testDependencyAwarePruning() {
        List<Event> events = generateEvents(1000, 4);
        PruningStrategy strategy = new DependencyAwarePruning();

        List<Event> pruned = strategy.prune(events);

        Assertions.assertTrue(pruned.size() <= events.size());
        Assertions.assertTrue(pruned.size() > 0);
    }

    @Test
    void testSamplingPruning() {
        List<Event> events = generateEvents(1000, 4);
        PruningStrategy strategy = new SamplingPruning(0.5);

        List<Event> pruned = strategy.prune(events);

        Assertions.assertTrue(pruned.size() < events.size());
        Assertions.assertTrue(pruned.size() >= events.size() * 0.4);
    }

    @Test
    void testAdaptivePruning() {
        PruningStrategy strategy = new AdaptivePruning();

        // Small dataset
        List<Event> small = generateEvents(50, 2);
        Assertions.assertEquals(small.size(), strategy.prune(small).size());

        // Large dataset
        List<Event> large = generateEvents(10000, 8);
        Assertions.assertTrue(strategy.prune(large).size() < large.size());
    }

    private List<Event> generateEvents(int count, int threads) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new Event(i % threads, "op" + i, i));
        }
        return events;
    }
}
