package phd.distributed.datamodel;

public class Event {
    private Object event;
    private int id;
    private int counter;

    public Event(int id, Object event, int counter){
        this.id = id;
        this.event = event;
        this.counter = counter;
    }

    public Object getEvent() {
        return event;
    }

    public int getId() {
        return id;
    }

    public int getCounter() {
        return counter;
    }

    @Override
    public String toString() {
        return "T" + id + ": " + event + " [" + counter + "]";
    }
}