package phd.experiments.aspectj;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import java.lang.reflect.Method;

@Aspect
public class PostOpMonitorAspect {

    public static volatile boolean ACTIVE = false;

    @Around("call(* java.lang.reflect.Method.invoke(..)) && within(phd.distributed.api.A)")
    public Object interceptAndLogPostOp(ProceedingJoinPoint pjp) throws Throwable {

        if (!ACTIVE) return pjp.proceed();

        int tid = NativeAspectJSnapshot.getCurrentLogicalTid();
        if (tid == -1) return pjp.proceed();

        long startTime = NativeTraceCollectorPostOp.markStart();

        Method method = (Method) pjp.getTarget();
        String methodName = method.getName();
        Object[] callArgs = pjp.getArgs();
        
        // --- FIX AQUÍ: Extracción segura de argumentos ---
        Object arg = null;
        if (callArgs != null && callArgs.length > 1 && callArgs[1] instanceof Object[]) {
            Object[] actualArgs = (Object[]) callArgs[1];
            if (actualArgs.length > 0) {
                arg = actualArgs[0]; // Solo accedemos si hay al menos un elemento
            }
        }
        // ------------------------------------------------

        Object result = pjp.proceed();

        NativeTraceCollectorPostOp.logCompleteOperation(tid, methodName, arg, result, startTime);

        return result;
    }
}