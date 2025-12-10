package phd.distributed.datamodel;

public class ThreadID {
    private final int id;

    public ThreadID(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ThreadID)) return false;
        ThreadID other = (ThreadID) obj;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "ThreadID{" + "id=" + id + '}';
    }
}