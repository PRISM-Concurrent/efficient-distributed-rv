package phd.distributed.api;

import phd.distributed.verifier.SeqUndoableQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AlgorithmLibrary {

    private static final Map<String, AlgorithmInfo> ALGORITHMS = new HashMap<>();

    static {
        // Sequential specifications
        register("SeqUndoableQueue", "Sequential undoable queue specification",
                AlgorithmCategory.QUEUES, SeqUndoableQueue.class);

        // Test algorithms (non-linearizable)
        register("BrokenQueue", "Deliberately broken queue (NOT linearizable)",
                AlgorithmCategory.QUEUES, phd.distributed.verifier.BrokenQueue.class);
        register("NonLinearizableQueue", "Queue with reordering violations (NOT linearizable)",
                AlgorithmCategory.QUEUES, phd.distributed.verifier.NonLinearizableQueue.class);

        // Java Concurrent Queues
        register("ConcurrentLinkedQueue", "Lock-free unbounded queue",
                AlgorithmCategory.QUEUES, ConcurrentLinkedQueue.class);
        register("LinkedBlockingQueue", "Blocking queue with linked nodes",
                AlgorithmCategory.QUEUES, LinkedBlockingQueue.class);
        register("ArrayBlockingQueue", "Bounded blocking queue backed by array",
                AlgorithmCategory.QUEUES, ArrayBlockingQueue.class);
        register("PriorityBlockingQueue", "Unbounded priority queue",
                AlgorithmCategory.QUEUES, PriorityBlockingQueue.class);
        register("LinkedTransferQueue", "Unbounded transfer queue",
                AlgorithmCategory.QUEUES, LinkedTransferQueue.class);

        // Java Concurrent Deques
        register("ConcurrentLinkedDeque", "Lock-free unbounded deque",
                AlgorithmCategory.QUEUES, ConcurrentLinkedDeque.class);
        register("LinkedBlockingDeque", "Blocking deque with linked nodes",
                AlgorithmCategory.QUEUES, LinkedBlockingDeque.class);

        // Java Concurrent Sets
        register("ConcurrentSkipListSet", "Concurrent sorted set",
                AlgorithmCategory.SETS, ConcurrentSkipListSet.class);
        register("CopyOnWriteArraySet", "Thread-safe set backed by copy-on-write array",
                AlgorithmCategory.SETS, CopyOnWriteArraySet.class);

        // Java Concurrent Maps
        register("ConcurrentHashMap", "Lock-free hash table",
                AlgorithmCategory.MAPS, ConcurrentHashMap.class);
        register("ConcurrentSkipListMap", "Concurrent sorted map",
                AlgorithmCategory.MAPS, ConcurrentSkipListMap.class);
    }

    private static void register(String name, String description,
                                AlgorithmCategory category, Class<?> implClass) {
        ALGORITHMS.put(name, new AlgorithmInfo(name, description, category, implClass));
    }

    public static List<AlgorithmInfo> listAll() {
        return new ArrayList<>(ALGORITHMS.values());
    }

    public static List<AlgorithmInfo> byCategory(AlgorithmCategory category) {
        return ALGORITHMS.values().stream()
            .filter(info -> info.getCategory() == category)
            .collect(Collectors.toList());
    }

    public static List<AlgorithmInfo> search(String query) {
        String lowerQuery = query.toLowerCase();
        return ALGORITHMS.values().stream()
            .filter(info -> info.getName().toLowerCase().contains(lowerQuery) ||
                          info.getDescription().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInstance(String algorithmName, Class<T> type) {
        AlgorithmInfo info = ALGORITHMS.get(algorithmName);
        if (info == null) {
            throw new IllegalArgumentException("Algorithm not found: " + algorithmName);
        }
        try {
            Object instance = info.getImplementationClass().getDeclaredConstructor().newInstance();
            return type.cast(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + algorithmName, e);
        }
    }

    public static Object getInstance(String algorithmName) {
        return getInstance(algorithmName, Object.class);
    }

    public static AlgorithmInfo getInfo(String algorithmName) {
        return ALGORITHMS.get(algorithmName);
    }

    public enum AlgorithmCategory {
        QUEUES, STACKS, SETS, MAPS, LOCKS, BARRIERS, COUNTERS
    }

    public static class AlgorithmInfo {
        private final String name;
        private final String description;
        private final AlgorithmCategory category;
        private final Class<?> implementationClass;

        public AlgorithmInfo(String name, String description,
                           AlgorithmCategory category, Class<?> implementationClass) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.implementationClass = implementationClass;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public AlgorithmCategory getCategory() {
            return category;
        }

        public Class<?> getImplementationClass() {
            return implementationClass;
        }

        @Override
        public String toString() {
            return String.format("%s (%s): %s", name, category, description);
        }
    }
}
