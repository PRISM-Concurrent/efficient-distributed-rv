package phd.distributed.snapshot;

import clojure.lang.IPersistentVector;

public abstract class Snapshot {
    public abstract void write(int id, Object invocation);
    public abstract void snapshot(int id, Object response);
    public abstract IPersistentVector buildXE();
    //public abstract Set<Event> scanAll();
}