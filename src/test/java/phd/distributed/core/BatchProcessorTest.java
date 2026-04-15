package phd.distributed.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
@Tag("fast")
class BatchProcessorTest {

    @Test
    void testBatchProcessing() {
        List<List<Integer>> batches = new ArrayList<>();
        BatchProcessor<Integer> processor = new BatchProcessor<>(3, batches::add);

        processor.add(1);
        processor.add(2);
        processor.add(3);

        assertEquals(1, batches.size());
        assertEquals(Arrays.asList(1, 2, 3), batches.get(0));
    }

    @Test
    void testFlush() {
        AtomicInteger count = new AtomicInteger(0);
        BatchProcessor<Integer> processor = new BatchProcessor<>(10,
            batch -> count.addAndGet(batch.size()));

        processor.add(1);
        processor.add(2);
        processor.flush();

        assertEquals(2, count.get());
    }
}
