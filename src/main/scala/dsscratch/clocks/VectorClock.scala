package dsscratch.clocks


class VectorClock(val id: Int, val vec: Map[Int, Int]) extends Clock {
  var tick = VecStamp(vec, id)

  def stamp(): TimeStamp = {
    tick = tick.inc()
    tick
  }

  def compareAndUpdate(other: TimeStamp): Unit = other match {
    case v @ VecStamp(_, _) => {
      if (tick <= v) tick = v.inc().withId(id)
      else if (tick >= v) tick = tick.inc()
      else tick = tick.mergeWith(v)
    }
    case _ => tick = tick.inc()
  }

  def snapshot: Clock = {
    val c = new VectorClock(id, vec)
    c.tick = tick
    c
  }
}

class DynamicVectorClock(val id: Int, val vec: Map[Int, Int]) extends Clock {
  var tick = DynVecStamp(vec, id)

  def stamp(): TimeStamp = {
    tick = tick.inc()
    tick
  }

  def compareAndUpdate(other: TimeStamp): Unit = other match {
    case v @ DynVecStamp(_, _) => {
      if (tick <= v) tick = v.inc().withId(id)
      else if (tick >= v) tick = tick.inc()
      else tick = tick.mergeWith(v)
    }
    case _ => tick = tick.inc()
  }

  def snapshot: Clock = {
    val c = new DynamicVectorClock(id, vec)
    c.tick = tick
    c
  }
}

object DynamicVectorClock {
  def apply(id: Int): DynamicVectorClock = new DynamicVectorClock(id, Map(id -> 0))
  def apply(id: Int, vec: Map[Int, Int]): DynamicVectorClock = new DynamicVectorClock(id, vec)
}