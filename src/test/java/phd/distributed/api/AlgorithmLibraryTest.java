package phd.distributed.api;

import org.junit.jupiter.api.Test;
import phd.distributed.verifier.SeqUndoableQueue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmLibraryTest {

    @Test
    void testListAll() {
        List<AlgorithmLibrary.AlgorithmInfo> all = AlgorithmLibrary.listAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(info -> info.getName().equals("SeqUndoableQueue")));
    }

    @Test
    void testByCategory() {
        List<AlgorithmLibrary.AlgorithmInfo> queues =
            AlgorithmLibrary.byCategory(AlgorithmLibrary.AlgorithmCategory.QUEUES);
        assertFalse(queues.isEmpty());
        assertTrue(queues.stream().allMatch(info ->
            info.getCategory() == AlgorithmLibrary.AlgorithmCategory.QUEUES));
    }

    @Test
    void testSearch() {
        List<AlgorithmLibrary.AlgorithmInfo> results = AlgorithmLibrary.search("queue");
        assertFalse(results.isEmpty());
    }

    @Test
    void testGetInstance() {
        Object instance = AlgorithmLibrary.getInstance("SeqUndoableQueue");
        assertNotNull(instance);
        assertTrue(instance instanceof SeqUndoableQueue);
    }

    @Test
    void testGetInstanceWithType() {
        SeqUndoableQueue queue = AlgorithmLibrary.getInstance("SeqUndoableQueue", SeqUndoableQueue.class);
        assertNotNull(queue);
    }

    @Test
    void testGetInstanceNotFound() {
        assertThrows(IllegalArgumentException.class, () ->
            AlgorithmLibrary.getInstance("NonExistent"));
    }
}
