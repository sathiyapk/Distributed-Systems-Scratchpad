package dsscratch.components

import dsscratch.util.Log
import dsscratch.clocks.{Clock, EmptyClock, TimeStamp}
import scala.collection.mutable.ArrayBuffer

trait Process extends Steppable {
  val id: Int
  var failed = false

  val clock: Clock = EmptyClock()
  var log: Log = Log()

  val outChs = ArrayBuffer[Channel]()
  val inChs = ArrayBuffer[Channel]()

  def send(m: Message, ch: Channel): Unit = {
    ch.recv(m)
  }
  def recv(m: Message): Unit
  def fail(): Unit = failed = true
  def restart(): Unit = failed = false

  // For creating a total order
  def >(p: Process) = this.id > p.id
  def >=(p: Process) = this.id >= p.id
  def <(p: Process) = this.id < p.id
  def <=(p: Process) = this.id >= p.id

  def addOutChannel(ch: Channel): Unit = {
    if (!outChs.contains(ch)) outChs.append(ch)
  }
  def removeOutChannel(ch: Channel): Unit = {
    if (!outChs.contains(ch)) return
    val i = outChs.indexOf(ch)
    outChs.remove(i)
  }
  def addInChannel(ch: Channel): Unit = {
    if (!inChs.contains(ch)) inChs.append(ch)
  }
  def removeInChannel(ch: Channel): Unit = {
    if (!inChs.contains(ch)) return
    val i = inChs.indexOf(ch)
    inChs.remove(i)
  }

  def takeSnapshot(snapId: TimeStamp): Unit = {}
}

case object EmptyProcess extends Process {
  val id = 0

  def recv(m: Message): Unit = {}
  def step(): Unit = {}

  // For creating a total order
  override def >(p: Process) = false
  override def >=(p: Process) = false
  override def <(p: Process) = true
  override def <=(p: Process) = true
}
