package phd.experiments.aspectj;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.Arrays;

/**
 * Instrumentación estilo El-Hokayem & Falcone (RV 2018).
 *
 * Usa @Before y @After separados — no @Around.
 * Esto reproduce exactamente la no-atomicidad que el paper analiza:
 * entre el @Before (logInvoke) y el @After (logReturn) puede ocurrir
 * un context switch, haciendo que la traza capturada difiera del
 * orden real de ejecución.
 *
 * El paper lo demuestra empíricamente: con AspectJ sin sincronización
 * adicional, ~50% de las trazas tienen un orden diferente al real.
 */
@Aspect
public class LinearizabilityMonitorAspect {

    public static volatile boolean ACTIVE = false;

    @AfterReturning(
        pointcut = "call(* java.lang.reflect.Method.invoke(..)) && within(phd.distributed.api.A)",
        returning = "result"
    )
    public void afterReflectiveCall(JoinPoint jp, Object result) {

        if (!ACTIVE) return;

        int tid = NativeAspectJSnapshot.getCurrentLogicalTid();
        if (tid == -1) return;

        // Extraer nombre del método
        String methodName = ((java.lang.reflect.Method) jp.getTarget()).getName();

        // Extraer argumento — igual que antes, del JoinPoint
        Object[] callArgs  = jp.getArgs();
        Object[] actualArgs = (callArgs.length > 1 && callArgs[1] instanceof Object[])
                ? (Object[]) callArgs[1]
                : new Object[0];

        Object rawArg = actualArgs.length == 0 ? null
                : (actualArgs.length == 1 ? actualArgs[0] : Arrays.asList(actualArgs));

        // Un solo evento que contiene todo: arg + result
        NativeTraceCollector.logOperation(tid, methodName, rawArg, result);
    }
}