package phd.distributed.api;
import java.util.List;
import phd.distributed.datamodel.MethodInf;

public interface DistAlgorithm {
    Object apply(MethodInf method, Object... args);
    List<MethodInf> methods();
}