package phd.distributed.verifier;

import phd.distributed.config.SystemConfig;
import phd.distributed.datamodel.Event;

import java.util.ArrayList;
import java.util.List;

public interface PruningStrategy {
    List<Event> prune(List<Event> events);

    static PruningStrategy getDefault() {
        return SystemConfig.FEATURES.smartPruning ?
            new PartialOrderReduction() : new NoPruning();
    }
}

class NoPruning implements PruningStrategy {
    @Override
    public List<Event> prune(List<Event> events) {
        return events;
    }
}

class PartialOrderReduction implements PruningStrategy {
    @Override
    public List<Event> prune(List<Event> events) {
        if (events.size() < 100) return events;

        // Simple pruning: remove redundant consecutive events from same thread
        List<Event> pruned = new ArrayList<>();
        Event prev = null;

        for (Event e : events) {
            if (prev == null || prev.getId() != e.getId()) {
                pruned.add(e);
            }
            prev = e;
        }

        return pruned;
    }
}
