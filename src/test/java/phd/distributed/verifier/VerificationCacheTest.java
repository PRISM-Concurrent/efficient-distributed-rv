package phd.distributed.verifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
@Tag("fast")
class VerificationCacheTest {

    private VerificationCache cache;

    @BeforeEach
    void setUp() {
        cache = new VerificationCache();
    }

    @Test
    void testCacheMiss() {
        List<Event> events = Arrays.asList(new Event(0, "op", 1));
        assertTrue(cache.get(events).isEmpty());
    }

    @Test
    void testCacheHit() {
        List<Event> events = Arrays.asList(new Event(0, "op", 1));
        cache.put(events, true, 100);

        Optional<VerificationCache.CachedResult> result = cache.get(events);
        assertTrue(result.isPresent());
        assertTrue(result.get().passed());
    }
}
