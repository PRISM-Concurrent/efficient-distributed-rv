package ox.cads.testing.adapters

import scala.collection.mutable
import scala.collection.mutable.Stack

/** 
 * Sequential FIFO queue with undo(), used as the sequential specification
 * for the LinearizabilityTester.
 * 
 * It extends scala.collection.mutable.Undoable (no type parameters)
 * and implements the same methods as a regular queue:
 *   - enqueue / dequeue
 *   - offer / poll
 *   - add / remove
 */
final class SeqUndoableQueue extends scala.collection.mutable.Undoable {
  private val q = new mutable.Queue[Int]()
  private val undoStack = new Stack[() => Unit]()

  /** Adds an element to the queue. */
  def enqueue(x: Int): Unit = {
    q.enqueue(x)
    // Record how to undo this operation
    undoStack.push(() => {
      if (q.nonEmpty) {
        val all = q.dequeueAll(_ => true)
        q.clear()
        all.dropRight(1).foreach(q.enqueue)
      }
    })
  }

  /** Removes and returns the first element; null if empty. */
  def dequeue: Any = {
    if (q.nonEmpty) {
      val v = q.dequeue()
      undoStack.push(() => q.prepend(v))
      v
    } else {
      undoStack.push(() => ())
      null
    }
  }

  // --- Aliases to match other queue APIs (offer/poll or add/remove) ---
  def offer(x: Int): Unit = enqueue(x)
  def poll: Any = dequeue

  def add(x: Int): Unit = enqueue(x)
  def remove: Any = dequeue
  // -------------------------------------------------------------------

  /** Undo the last operation, restoring the previous state. */
  override def undo(): Unit = {
    val a = undoStack.pop()
    a()
  }

  override def toString: String = s"SeqUndoableQueue(${q.mkString(",")})"
}
