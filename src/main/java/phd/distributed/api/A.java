package phd.distributed.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import phd.distributed.datamodel.MethodInf;

public class A implements DistAlgorithm {

    private final List<MethodInf> methodList;
    private final Object instance;
    private static final Logger LOGGER = LogManager.getLogger();

    public A(String className) {
        Object tempInstance = null;
        List<MethodInf> lista = new ArrayList<>();
        try {
            Class<?> execClass = Class.forName(className);

            // Special handling for String class to create a non-empty instance
            if (className.equals("java.lang.String")) {
                tempInstance = "Hello World"; // Create a non-empty string for testing
            } else {
                tempInstance = execClass.getDeclaredConstructor().newInstance();
            }

            for (Method method : execClass.getMethods()) {
                String name = method.getName();

                // Métodos peligrosos o sin utilidad para ejecución reflejada
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (name.equals("wait") || name.equals("notify") || name.equals("notifyAll")) {
                    continue;
                }
                if (name.equals("parallelStream") || name.equals("spliterator")) {
                    continue;
                }
                if (name.equals("getClass") || name.equals("equals") || name.equals("hashCode") || name.equals("toString")) {
                    continue;
                }

                lista.add(new MethodInf(method));
            }
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            LOGGER.error("We got an error in class A", e);
        }
        this.instance = tempInstance;
        this.methodList = List.copyOf(lista);
    }

    /**
     * Constructor que solo guarda los métodos cuyo nombre coincide exactamente
     * con alguno de los proporcionados en methodNames.
     *
     * Si hay sobrecargas (mismo nombre con distinta firma), se queda con
     * la versión con MENOR número de parámetros.
     *
     * Si algún nombre no se encuentra, se imprime un warning y se muestran
     * los métodos disponibles de la clase.
     */
    public A(String className, String... methodNames) {
        Object tempInstance = null;
        List<MethodInf> lista = new ArrayList<>();

        try {
            Class<?> execClass = Class.forName(className);

            // Crear instancia
            if (className.equals("java.lang.String")) {
                tempInstance = "Hello World";
            } else {
                tempInstance = execClass.getDeclaredConstructor().newInstance();
            }

            // Conjunto de nombres que queremos exactamente
            Set<String> desired = new HashSet<>(Arrays.asList(methodNames));

            // Para poder reportar qué métodos tiene la clase (puede incluir duplicados)
            List<String> availableMethodNames = new ArrayList<>();

            // Usamos un mapa nombre -> mejor MethodInf (menos parámetros)
            Map<String, MethodInf> unique = new LinkedHashMap<>();

            for (Method method : execClass.getMethods()) {
                String name = method.getName();
                availableMethodNames.add(name);

                // Filtros de métodos peligrosos / inútiles
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (name.equals("wait") || name.equals("notify") || name.equals("notifyAll")) {
                    continue;
                }
                if (name.equals("parallelStream") || name.equals("spliterator")) {
                    continue;
                }
                if (name.equals("getClass") || name.equals("equals") ||
                    name.equals("hashCode") || name.equals("toString")) {
                    continue;
                }

                // Solo nos interesan los nombres deseados
                if (!desired.contains(name)) {
                    continue;
                }

                MethodInf candidate = new MethodInf(method);
                MethodInf existing  = unique.get(name);

                // Si no había uno, o este tiene MENOS parámetros, lo preferimos
                if (existing == null ||
                    method.getParameterCount() < existing.getMethod().getParameterCount()) {
                    unique.put(name, candidate);
                }
            }

            // Pasar del mapa a la lista final
            lista.addAll(unique.values());

            // Revisar cuáles nombres pedidos NO se encontraron
            for (String wanted : desired) {
                boolean found = lista.stream()
                                     .anyMatch(mi -> mi.getName().equals(wanted));
                if (!found) {
                    LOGGER.warn(
                        "Requested method '{}' not found in class {}. " +
                        "Available method names include: {}",
                        wanted, className, availableMethodNames
                    );
                }
            }

            if (lista.isEmpty()) {
                LOGGER.warn(
                    "No requested methods were found in class {}. " +
                    "Available method names are: {}",
                    className, availableMethodNames
                );
            } else {
                LOGGER.info(
                    "Initialized A for class {} with methods: {}",
                    className,
                    lista.stream().map(MethodInf::getName).toList()
                );
            }

        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException |
                 InstantiationException | NoSuchMethodException | SecurityException |
                 InvocationTargetException e) {
            LOGGER.error("We got an error in A(String, String...) for class {}", className, e);
        }

        this.instance   = tempInstance;
        this.methodList = List.copyOf(lista);
    }
    // Constructor 2

    @Override
    public Object apply(MethodInf m, Object... args) {
        if (m == null || this.instance == null) {
            LOGGER.warn("Method or instance is null, returning null");
            return null;
        }

        Method method = m.getMethod();
        LOGGER.info("Invoking: {} with args: {}", method.getName(), Arrays.toString(args));

        try {
            String methodName = method.getName();
            if (isStateDependent(methodName) && isEmpty()) {
                LOGGER.warn("Skipping {} on empty collection", methodName);
                return null;
            }

            int expectedParams = method.getParameterCount();
            int actualParams = (args == null) ? 0 : args.length;

            if (expectedParams != actualParams) {
                LOGGER.warn("Parameter count mismatch for {}: expected {}, got {}", methodName, expectedParams, actualParams);
                return null;
            }

            return method.invoke(this.instance, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Error Invoking {}", method.getName(), e);
            return null;
        }
    }

    @Override
    public List<MethodInf> methods() {
        return this.methodList;
    }

    /**
     * Checks if a method is state-dependent and might fail on empty collections
     */
    private boolean isStateDependent(String methodName) {
        return methodName.equals("remove") || methodName.equals("removeLast") ||
               methodName.equals("removeFirst") || methodName.equals("element") ||
               methodName.equals("peek") || methodName.equals("poll") ||
               methodName.equals("pop") || methodName.equals("get") ||
               methodName.equals("charAt");
    }

    /**
     * Checks if the instance is an empty collection
     */
    private boolean isEmpty() {
        if (this.instance == null) {
            return true;
        }

        try {
            if (this.instance instanceof java.util.Collection) {
                return ((java.util.Collection<?>) this.instance).isEmpty();
            }
            if (this.instance instanceof java.util.Map) {
                return ((java.util.Map<?, ?>) this.instance).isEmpty();
            }
            if (this.instance instanceof String) {
                return ((String) this.instance).isEmpty();
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking if instance is empty", e);
        }

        return false;
    }
}
