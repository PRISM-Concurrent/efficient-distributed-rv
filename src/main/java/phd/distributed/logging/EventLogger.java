package phd.distributed.logging;

import phd.distributed.datamodel.Event;

public interface EventLogger {
    void logEvent(Event event);
    void shutdown();
}
