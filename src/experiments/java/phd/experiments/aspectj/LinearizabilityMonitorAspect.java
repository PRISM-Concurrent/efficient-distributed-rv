package phd.experiments.aspectj;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
public class LinearizabilityMonitorAspect {

    public static volatile boolean ACTIVE = false;

    @Around("call(* java.lang.reflect.Method.invoke(..)) && within(phd.distributed.api.A)")
    public Object interceptReflectiveCall(ProceedingJoinPoint pjp) throws Throwable {

        if (!ACTIVE) return pjp.proceed();

        int tid = NativeAspectJSnapshot.getCurrentLogicalTid();
        if (tid == -1) return pjp.proceed();

        Method method = (Method) pjp.getTarget();
        String methodName = method.getName();

        Object[] callArgs  = pjp.getArgs();
        Object[] actualArgs;
        if (callArgs.length > 1 && callArgs[1] instanceof Object[]) {
            actualArgs = (Object[]) callArgs[1];
        } else {
            actualArgs = new Object[0];
        }

        // 🔥 EXTRAEMOS EL OBJETO CRUDO (sin pasarlo a String)
        Object rawArg = actualArgs.length == 0 ? null : 
                       (actualArgs.length == 1 ? actualArgs[0] : Arrays.asList(actualArgs));

        // 1. Log Invoke (pasando rawArg)
        NativeTraceCollector.logInvoke(tid, methodName, rawArg);

        // 2. Ejecutar la operación real
        Object result = pjp.proceed();

        // 3. Log Return (pasando result intacto)
        NativeTraceCollector.logReturn(tid, result);

        return result;
    }
}