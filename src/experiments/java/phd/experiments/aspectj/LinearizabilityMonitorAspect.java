package phd.experiments.aspectj;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.Arrays;

/**
 * Instrumentation style based on El-Hokayem & Falcone (RV 2018).
 * * This aspect uses @AfterReturning to intercept Method.invoke calls within A.apply().
 * The event is captured only after the operation has returned. Consequently, the 
 * interval between the actual execution of the operation and the event capture 
 * is not atomic: a context switch can occur during this gap, causing the 
 * captured trace to potentially differ from the real-time execution order.
 * * The original paper empirically demonstrates that, without additional 
 * synchronization, approximately 50% of the resulting traces might exhibit 
 * an order that does not match the actual execution (Variant 2).
 */
@Aspect
public class LinearizabilityMonitorAspect {

    /** Global flag to enable or disable trace collection during experiments. */
    public static volatile boolean ACTIVE = false;

    /**
     * Advice that intercepts reflective method calls to the target algorithm.
     * * The pointcut targets Method.invoke within the A API class, specifically 
     * capturing the returned object.
     * * @param jp The JoinPoint providing metadata about the reflective call.
     * @param result The object returned by the invoked method.
     */
    @AfterReturning(
        pointcut = "call(* java.lang.reflect.Method.invoke(..)) && within(phd.distributed.api.A)",
        returning = "result"
    )
    public void afterReflectiveCall(JoinPoint jp, Object result) {

        if (!ACTIVE) return;

        // Retrieve the logical TID stored in the Snapshot thread-local.
        int tid = NativeAspectJSnapshot.getCurrentLogicalTid();
        if (tid == -1) return;

        // Extract the name of the method being invoked reflectively.
        String methodName = ((java.lang.reflect.Method) jp.getTarget()).getName();

        // Extract the arguments from the JoinPoint metadata.
        // In Method.invoke(target, args[]), the actual arguments are in the second position.
        Object[] callArgs  = jp.getArgs();
        Object[] actualArgs = (callArgs.length > 1 && callArgs[1] instanceof Object[])
                ? (Object[]) callArgs[1]
                : new Object[0];

        // Format the argument for the trace: 
        // null if empty, the object itself if singular, or a list if multiple arguments exist.
        Object rawArg = actualArgs.length == 0 ? null
                : (actualArgs.length == 1 ? actualArgs[0] : Arrays.asList(actualArgs));

        // Log a single operation event containing both the input (arg) and output (result).
        NativeTraceCollector.logOperation(tid, methodName, rawArg, result);
    }
}