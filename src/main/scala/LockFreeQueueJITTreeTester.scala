
import ox.cads.testing._
import ox.cads.testing.adapters.SeqUndoableQueue
import ox.cads.collection.LockFreeQueue
import scala.util.Random

/** Tester para LockFreeQueue[Int] usando JITTree (JITLinUndoTester). */
object LockFreeQueueJITTreeTester {

  type C = LockFreeQueue[Int]
  type S = SeqUndoableQueue

  /** Worker parametrizado por #iters, prob. de enqueue y rango de valores. */
  def mkWorker(iters: Int, enqProb: Double = 0.35, maxValue: Int = 20)
    : LinearizabilityTester.WorkerType[S, C] =
    (me: Int, log: GenericThreadLog[S, C]) => {
      val rnd = new Random(me * 2654435761L ^ System.nanoTime())
      def concDeqAsAny(q: C): Any = q.dequeue match {
        case Some(v) => v
        case None    => null
      }
      for (_ <- 0 until iters) {
        if (rnd.nextFloat() <= enqProb) {
          val x = rnd.nextInt(maxValue)
          // concOp: _.enqueue(x)  |  seqOp: _.enqueue(x)
          log.log[Unit, Unit](_.enqueue(x), s"enqueue($x)", _.enqueue(x))
        } else {
          // concOp devuelve Any (valor o null) para empatar con S.dequeue
          log.log[Any, Any](concDeqAsAny, "dequeue", _.dequeue)
        }
      }
    }

  /** Ejecuta varias historias y afirma linealizabilidad (>0 = éxito/terminado). */
  def main(args: Array[String]): Unit = {
    println("In ----------------------.")
    val p      = if (args.nonEmpty) args(0).toInt else 6        // hilos
    val iters  = if (args.length > 1) args(1).toInt else 1000   // iters/hilo
    val trials = if (args.length > 2) args(2).toInt else 100    // historias

    for (_ <- 0 until trials) {
      val concQ = new C                         // cola concurrente compartida
      val seqQ  = new S                         // especificación secuencial undoable
      val worker = mkWorker(iters, enqProb = 0.35, maxValue = 20)

      // Construye tester JITTree (usa JITLinUndoTester por dentro)
      val tester = LinearizabilityTester.JITTree[S, C](
        seqQ, concQ, p, worker, iters, tsLog = true, maxSize = -1
      ) // :contentReference[oaicite:2]{index=2}

      val result = tester() // corre trabajadores, registra, verifica
      assert(result > 0, "Se encontró una historia no linealizable")
    }
    println("✅ Todas las historias verificaron linealizabilidad con JITTree.")
  }
}