package dsscratch.components

import dsscratch.clocks.{TS, TimeStamp}

case class Message(cmd: Command, sender: Process, ts: TimeStamp = TS(-1, -1)) {
  override def toString: String = "Message from " + sender + ": " + ts + " " + cmd
}
