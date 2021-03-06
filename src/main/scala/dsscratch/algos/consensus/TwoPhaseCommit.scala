package dsscratch.algos.consensus

import dsscratch.components._
import dsscratch.clocks._
import dsscratch.algos._
import dsscratch.algos.nodes._

import scala.collection.mutable.{Map => mMap, ArrayBuffer, Set => mSet}

// STILL WORK IN PROGRESS
// Broadcast protocols
// currently only process one message in total
// but this protocol requires broadcasting multiple messages

// An initiator sends a message to all other nodes
// asking for a vote (either yes or abort).
//
// If even one vote is abort, initiator sends out
// abort message.
//
// If vote is unanimously yes, initiator sends out
// commit message.

trait TwoPhaseCommitLocalState extends LocalState {
  var initiated: Boolean
  var initiator: Boolean
  var curVote: Option[Command]
  var initiatedCmds: mSet[Command]
  var votes: mMap[Command, mMap[Process, TwoPCVote]]
  var votedOn: mSet[Command]
  var committed: mSet[Command]
  var networkNodes: Seq[Process]
}

object TwoPhaseCommitComponent {
  def apply(parentProcess: Process, nodes: Seq[Process], isInitiator: Boolean = false): TwoPhaseCommitComponent = {
    new TwoPhaseCommitComponent(parentProcess, nodes, isInitiator)
  }
  def buildWith(parentProcess: Process, s: TwoPhaseCommitLocalState): TwoPhaseCommitComponent = {
    val newC = TwoPhaseCommitComponent(parentProcess, s.networkNodes, s.initiator)

    newC.s.initiated = s.initiated
    newC.s.curVote = s.curVote
    newC.s.initiatedCmds = s.initiatedCmds
    newC.s.votedOn = s.votedOn
    newC.s.committed = s.committed
    newC
  }
}

class TwoPhaseCommitComponent(val parentProcess: Process, nodes: Seq[Process], isInitiator: Boolean = false) extends NodeComponent {
  val algoCode: AlgoCode = AlgoCodes.TWO_PHASE_COMMIT
  val outChs: ArrayBuffer[Channel] = parentProcess.outChs
  val inChs: ArrayBuffer[Channel] = parentProcess.inChs

  ////////////////////
  //LOCAL STATE
  private object s extends TwoPhaseCommitLocalState {
    var initiated = false
    var initiator: Boolean = isInitiator
    var curVote: Option[Command] = None
    var initiatedCmds: mSet[Command] = mSet[Command]()
    var votes = mMap[Command, mMap[Process, TwoPCVote]]()
    var votedOn = mSet[Command]()
    var committed = mSet[Command]()
    var networkNodes = nodes
  }
  ////////////////////

  def processMessage(m: Message): Unit = {
    m.cmd match {
      case Commit(cmd, sender, ts) => {
        val initiateTwoPC = InitiateTwoPC(cmd, sender, ts)
        parentProcess.recv(Message(initiateTwoPC, parentProcess, clock.stamp()))
      }
      case InitiateTwoPC(cmd, _, _)  => if (m.sender == parentProcess) initiate2PC(cmd)
      case TwoPCVoteRequest(cmd, _, _) => {
        if (isInitiatorFor(cmd)) return
        if (!s.votedOn.contains(cmd)) sendVote(cmd)
      }
      case r @ TwoPCVoteReply(vote, cmd, process) => if (isInitiatorFor(cmd)) registerReply(r)
      case TwoPCCommit(cmd, _, _) => commit(cmd)
      case TwoPCAbort(cmd, _, _) => abort(cmd)
      case _ => // Ignore the rest
    }
  }

  //checks for one successful voting round, whether or not it was aborted
  def terminated: Boolean = s.committed.nonEmpty

  def step(): Unit = {
    if (parentProcess.failed) return
    if (terminated) return
    // Nothing to do...
  }

  def snapshot: TwoPhaseCommitComponent = TwoPhaseCommitComponent.buildWith(parentProcess, s)

  def result = "" // For printing results

  private def initiate2PC(cmd: Command): Unit = {
    val voteRequest = TwoPCVoteRequest(cmd, parentProcess, clock.stamp())
    val initiateBroadcastMsg = Message(Broadcast(voteRequest, parentProcess, clock.stamp()), parentProcess, clock.stamp())
    s.initiatedCmds += cmd
    s.votedOn += cmd
    s.votes.update(cmd, mMap[Process, TwoPCVote]())
    parentProcess.recv(initiateBroadcastMsg)
  }

  private def registerReply(r: TwoPCVoteReply) = r match {
    case TwoPCVoteReply(vote, cmd, p) => {
      s.votes(cmd).update(p, vote)
      if (allVotesReceived(cmd)) checkForSuccessfulVoteFor(cmd)
    }
  }

  private def allVotesReceived(cmd: Command): Boolean = {
    nodes.filter(_ != parentProcess).forall(p => {
      s.votes(cmd).contains(p)
    })
  }

  private def checkForSuccessfulVoteFor(cmd: Command): Unit = {
    val success = voteSucceedsFor(cmd)
    val result = if (success) TwoPCCommit(cmd, parentProcess, clock.stamp()) else TwoPCAbort(cmd, parentProcess, clock.stamp())
    val initiateBroadcastMsg = Message(Broadcast(result, parentProcess, clock.stamp()), parentProcess, clock.stamp())
    parentProcess.recv(initiateBroadcastMsg)
    s.committed += cmd
    if (success) parentProcess.recv(Message(cmd, parentProcess, clock.stamp()))
  }

  private def sendVote(cmd: Command): Unit = {
    val vote = if (s.curVote.isEmpty) TwoPCVoteCommit else TwoPCVoteAbort
    val reply = TwoPCVoteReply(vote, cmd, parentProcess)
    val initiateBroadcastMsg = Message(Broadcast(reply, parentProcess, clock.stamp()), parentProcess, clock.stamp())
    parentProcess.recv(initiateBroadcastMsg)
    s.votedOn += cmd
  }

  private def commit(cmd: Command): Unit = {
    val deliverable = Message(cmd, parentProcess, clock.stamp())
    parentProcess.recv(deliverable)
    s.committed += cmd
    s.curVote = None
  }

  private def abort(cmd: Command): Unit = {
    s.curVote = None
  }

  private def voteSucceedsFor(cmd: Command): Boolean = {
    (for (k <- s.votes(cmd).keys) yield isCommitVote(s.votes(cmd)(k))).forall(x => x)
  }

  private def isCommitVote(vote: TwoPCVote): Boolean = vote match {
    case TwoPCVoteCommit => true
    case TwoPCVoteAbort => false
  }

  private def isInitiatorFor(cmd: Command): Boolean = s.initiatedCmds.contains(cmd)
}

