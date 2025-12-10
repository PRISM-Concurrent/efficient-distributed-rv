package phd.distributed;
import phd.distributed.api.A;
 import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;
public class App {
    public static void main(String[] args) {
        String nameString = "java.util.concurrent.ConcurrentLinkedQueue";

        DistAlgorithm alg = new A(nameString);

        int operations = 10;
        int cores = Runtime.getRuntime().availableProcessors();

        Executioner executioner = new Executioner(cores,operations, alg);

        executioner.taskProducers();
        executioner.taskVerifiers();

    }
}
