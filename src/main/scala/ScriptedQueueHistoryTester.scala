
import ox.cads.testing._
import ox.cads.testing.adapters.SeqUndoableQueue

object ScriptedQueueHistoryTester {

  // --- descripción de la historia ---
  sealed trait Step
  case class Enq(x: Int) extends Step
  /** expect: valor o null si el dequeue en tu historia devuelve vacío */
  case class Deq(expect: Any) extends Step

  def run(script: Vector[List[Step]], verbose: Boolean = true): Int = {
    type S = SeqUndoableQueue
    type C = Unit

    val p = script.size
    val iters = script.map(_.size).max
    val totalInvocs = script.map(_.size).sum

    val seqObj = new S
    val concObj = () // dummy

    // 1) creamos solver
    val solver = new JITLinUndoTester[S](seqObj, p, maxSize = -1, verbose = verbose)

    // 2) log compartido
    val shared = new SharedLog[S, C](totalInvocs, concObj, solver.mkInvoke, solver.mkReturn)

    // 3) llenar el log según nuestro orden
    for (round <- 0 until iters) {
      for (t <- 0 until p) {
        script(t).lift(round).foreach {
          case Enq(x) =>
            shared(t).log[Unit, Unit](_ => (), s"enqueue($x)", _.enqueue(x))

          case Deq(expect) =>
            shared(t).log[Any, Any](_ => expect, "dequeue", _.dequeue)
        }
      }
    }

    // 4) ver qué se registró
    println("=== LOG REGISTRADO ===")
    shared.getLog.foreach(ev => println(ev))

    // 5) correr solver
    val res =
      try {
        solver.solve(shared.getLog)
      } catch {
        case e: Throwable =>
          println("⚠️ Solver lanzó excepción:")
          e.printStackTrace()
          -1
      }

    if (res > 0) {
      println("✅ Historia linealizable.")
    } else {
      println("❌ Historia NO linealizable.")
    }

    res
  }

  def main(args: Array[String]): Unit = {
    // ejemplo muy simple
    val script = Vector(
      List(Enq(2), Deq(2)),          // hilo 0
      List(Deq(null), Enq(3), Deq(3)) // hilo 1
    )

    val res = run(script, verbose = true)

    // IMPORTANTE: no truenes Maven
    // si quieres que Maven falle cuando no sea linealizable, cambia este if
    if (res <= 0) {
      // solo avisamos
      println("Testeamos historia y NO fue linealizable (pero no trono el build).")
    }
  }
}
