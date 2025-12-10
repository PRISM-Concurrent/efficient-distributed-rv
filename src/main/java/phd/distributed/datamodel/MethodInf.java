package phd.distributed.datamodel;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class MethodInf {
    private Method method;
    private String name;
    private final List<Type> typeParam;
    private final Type typeReturn;

    public MethodInf(Method method) {
        this.name = method.getName();
        this.method = method;
        this.typeParam = Arrays.asList(method.getGenericParameterTypes());
        this.typeReturn = method.getGenericReturnType();
    }

    public Class<?>[] getParameterTypes() {
    return method.getParameterTypes();
}

    public String getName() {
        return name;
    }

    public Method getMethod() {
        return method;
    }

    public List<Type> getTypeParam() {
        return typeParam;
    }

    public Type getTypeReturn() {
        return typeReturn;
    }

    @Override
    public String toString() {
        return "Method: " + name +
               "\n  Args: " + typeParam +
               "\n  Return: " + typeReturn;
    }
}
