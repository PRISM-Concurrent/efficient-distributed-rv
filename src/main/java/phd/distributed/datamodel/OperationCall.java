package phd.distributed.datamodel;

import java.util.Arrays;
import java.util.List;

import phd.distributed.api.DistAlgorithm;

public class OperationCall {

    private final Object args;
    private final MethodInf method;

    public OperationCall(Object args, MethodInf method) {
        this.args = args;
        this.method = method;
    }

    /**
     * Returns the arguments as a String.
     * Handles: null, single objects, and arrays.
     */
    public Object args() {
        return args;
    }

    public String argsAsString() {
    if (args == null) {
        return null;
    }
    if (args.getClass().isArray()) {
        return Arrays.deepToString((Object[]) args);
    }
    return args.toString();
}

    public MethodInf method() {
        return method;
    }

    /**
     * Construye una OperationCall para un método concreto usando ValueGenerator,
     * igual que chooseOp, pero sin elegir el método al azar.
     */
    public static OperationCall fromMethod(DistAlgorithm alg,
                                           MethodInf methodInf,
                                           int processId) {
        Class<?>[] paramTypes = methodInf.getParameterTypes();

        Object args;
        switch (paramTypes.length) {
            case 0 -> args = null;
            case 1 -> args = ValueGenerator.getValue(paramTypes[0], processId);
            default -> {
                Object[] multiArgs = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    multiArgs[i] = ValueGenerator.getValue(paramTypes[i], processId + i);
                }
                args = multiArgs;
            }
        }

        return new OperationCall(args, methodInf);
    }

    /**
     * Versión antigua: elige método aleatorio y delega en fromMethod.
     */
    public static OperationCall chooseOp(DistAlgorithm alg, int processId) {
        List<MethodInf> methods = alg.methods();
        int operationIndex = (int) (Math.random() * methods.size());
        MethodInf methodInf = methods.get(operationIndex);
        return fromMethod(alg, methodInf, processId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("op ").append(method().getName()).append("(");

        if (args == null) {
            sb.append("null");
        } else if (args.getClass().isArray()) {
            Object[] argArray = (Object[]) args;
            for (int i = 0; i < argArray.length; i++) {
                sb.append(argArray[i]);
                if (i < argArray.length - 1) {
                    sb.append(", ");
                }
            }
        } else {
            sb.append(args);
        }

        sb.append(")");
        return sb.toString();
    }

}
