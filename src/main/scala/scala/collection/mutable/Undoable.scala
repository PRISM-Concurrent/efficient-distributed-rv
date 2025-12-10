package scala.collection.mutable

/** 
 * Dummy replacement for the old Undoable trait removed after Scala 2.9.x.
 * If your code only needs the type for compilation, this will satisfy it.
 */
trait Undoable {
  def undo(): Unit
}