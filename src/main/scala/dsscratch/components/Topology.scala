package dsscratch.components

import randomific.Rand
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

case class Topology[P](nodes: Seq[P], chs: Seq[Channel])

object Topology {
  def createTwoChannel(p0: Process, p1: Process, chs: ArrayBuffer[Channel]): Unit = {
    val newCh = TwoChannel(p0, p1)
    p0.addChannel(newCh)
    p1.addChannel(newCh)
    chs.append(newCh)
  }

  def createMultiChannel(ps: Seq[Process], chs: ArrayBuffer[Channel]): Unit = {
    val newCh = MultiChannel(ps)
    for (p <- ps) p.addChannel(newCh)
    chs.append(newCh)
  }

  def star[P <: Process](center: P, others: Seq[P]): Topology[P] = {
    val chs = ArrayBuffer[Channel]()
    for (nd <- others) createTwoChannel(center, nd, chs)

    Topology(center +: others, chs)
  }

  def bus[P <: Process](nodes: Seq[P]): Topology[P] = {
    val chs = ArrayBuffer[Channel]()
    createMultiChannel(nodes, chs)
    Topology(nodes, chs)
  }

  def line[P <: Process](nodes: Seq[P]): Topology[P] = {
    assert(nodes.size > 1)
    val chs = ArrayBuffer[Channel]()
    def loop(s: Seq[P], acc: Seq[P]): Seq[P] = s match {
      case a if s.isEmpty => acc.reverse
      case b if b.tail.isEmpty => (b.head +: acc).reverse
      case c => {
        createTwoChannel(s.head, s.tail.head, chs)
        loop(s.tail, s.head +: acc)
      }
    }
    val finalNodes = loop(nodes, Seq[P]())
    Topology(finalNodes, chs)
  }

  def ring[P <: Process](nodes: Seq[P]): Topology[P] = {
    assert(nodes.size > 1)
    val shuffled: Seq[P] = Random.shuffle(nodes)

    val l: Topology[P] = line(shuffled)
    val chs: ArrayBuffer[Channel] = ArrayBuffer(l.chs: _*)
    val last: P = l.nodes.reverse.head

    createTwoChannel(l.nodes.head, last, chs)
    Topology(l.nodes, chs)
  }

  def totallyConnected[P <: Process](nodes: Seq[P]): Topology[P] = {
    assert(nodes.size > 1)
    val chs = ArrayBuffer[Channel]()
    val shuffled: Seq[P] = Random.shuffle(nodes)

    val pairs = cProductNoSelfPairs(shuffled)

    for (e: (Process, Process) <- pairs) {
      createTwoChannel(e._1, e._2, chs)
    }

    Topology(shuffled, chs)
  }

  def connectedWithKMoreEdges[P <: Process](k: Int, nodes: Seq[P]): Topology[P] = {
    assert(nodes.size > 1 && k <= (((nodes.size * nodes.size) - (2 * nodes.size)) + 1)) //(n^2 - n) - (n - 1) = (n^2 - 2n) + 1 possible extra edges
    val shuffled: Seq[P] = Random.shuffle(nodes)

    val l: Topology[P] = line(shuffled) //Ensures connected path
    val chs = ArrayBuffer(l.chs: _*)
    val pairs = cProductNoSelfPairs(l.nodes)
    println(pairs)

    val extraEdges = Rand.pickKItems(k, pairs)

    for (e: (Process, Process) <- extraEdges) {
      createTwoChannel(e._1, e._2, chs)
    }

    val distinctChs = distinctTwoChs(chs)

    Topology(l.nodes, distinctChs) //Some edges may have been chosen twice
  }

  private def distinctTwoChs(as: Seq[Channel]): Seq[Channel] = {
    def loop(s: Seq[Channel], acc: Seq[Channel]): Seq[Channel] = s match {
      case a if a.isEmpty => acc
      case b if b.tail.isEmpty => acc
      case c => if (!s.tail.exists(x => x is s.head)) loop(s.tail, s.head +: acc) else loop(s.tail, acc)
    }
    loop(as, Seq[Channel]())
  }

  private def cProductNoSelfPairs(s: Seq[Process]): Seq[(Process, Process)] = {
    (for (
      x: Process <- s;
      y: Process <- s
    ) yield (x, y)).filter(pair => pair._1 != pair._2)
  }

}