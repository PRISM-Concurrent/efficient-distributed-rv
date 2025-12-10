import ox.cads.util._

object SimpleExperiments {
  def main(args: Array[String]): Unit = {
    println("Running simple Scala experiments...")
    
    // Test Spin utility
    spinTest()
  }
  
  private def spinTest(): Unit = {
    println("\n=== Spin Timing Test ===")
    val spinCount = 1000L
    val reps = 100
    
    val start = System.nanoTime()
    for (_ <- 1 to reps) {
      Spin(spinCount)
    }
    val end = System.nanoTime()
    
    val avgTime = (end - start) / reps
    println(s"Spin($spinCount): $avgTime ns average")
    println("Scala experiment completed successfully!")
  }
}
