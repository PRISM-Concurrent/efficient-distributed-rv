package phd.distributed.verifier;

import phd.distributed.datamodel.Event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AdvancedPruningStrategies {

    public static class DependencyAwarePruning implements PruningStrategy {
        @Override
        public List<Event> prune(List<Event> events) {
            if (events.size() < 100) return events;

            // Keep first and last event per thread, plus events with unique operations
            Map<Integer, List<Event>> byThread = events.stream()
                .collect(Collectors.groupingBy(Event::getId));

            List<Event> pruned = new ArrayList<>();
            for (List<Event> threadEvents : byThread.values()) {
                if (threadEvents.isEmpty()) continue;

                pruned.add(threadEvents.get(0)); // First
                if (threadEvents.size() > 2) {
                    // Add middle events with unique operations
                    Set<String> seen = new HashSet<>();
                    for (int i = 1; i < threadEvents.size() - 1; i++) {
                        String op = String.valueOf(threadEvents.get(i).getEvent());
                        if (seen.add(op)) {
                            pruned.add(threadEvents.get(i));
                        }
                    }
                }
                if (threadEvents.size() > 1) {
                    pruned.add(threadEvents.get(threadEvents.size() - 1)); // Last
                }
            }

            pruned.sort(Comparator.comparingInt(Event::getCounter));
            return pruned;
        }
    }

    public static class SamplingPruning implements PruningStrategy {
        private final double sampleRate;

        public SamplingPruning(double sampleRate) {
            this.sampleRate = Math.max(0.1, Math.min(1.0, sampleRate));
        }

        @Override
        public List<Event> prune(List<Event> events) {
            if (events.size() < 100) return events;

            int targetSize = (int) (events.size() * sampleRate);
            int step = Math.max(1, events.size() / targetSize);

            List<Event> pruned = new ArrayList<>();
            for (int i = 0; i < events.size(); i += step) {
                pruned.add(events.get(i));
            }

            return pruned;
        }
    }

    public static class AdaptivePruning implements PruningStrategy {
        @Override
        public List<Event> prune(List<Event> events) {
            if (events.size() < 100) return events;

            // Choose strategy based on event count
            if (events.size() < 1000) {
                return new PartialOrderReduction().prune(events);
            } else if (events.size() < 10000) {
                return new DependencyAwarePruning().prune(events);
            } else {
                return new SamplingPruning(0.5).prune(events);
            }
        }
    }
}
