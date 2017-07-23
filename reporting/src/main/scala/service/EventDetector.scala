package com.bestmile
package service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import model._

abstract class EventDetector[T] {
  var event: Option[T] = None

  def <<(m: Event): Boolean
  def getFlow: Flow[Event, T, NotUsed] = {
    Flow[Event]
      .collect { case m if this << m =>
        val e = event
        event = None
        e.getOrElse(sys.error("event not set"))
      }
  }
}

class UserWaitDetector extends EventDetector[UserWait] {
  import collection.mutable.Map
  val waiting: Map[User.ID, Created] = Map[User.ID, Created]()

  override def <<(m: Event): Boolean = m match {
    case c: Created =>
      waiting += (c.userID -> c)
      false

    case p: PickedUp =>
      waiting.get(p.userID)
        .map(c => UserWait(DateTime.now, c, p))
        .fold(false) { evt =>
          waiting -= p.userID
          event = Some(evt)
          true
        }

    case _ => false
  }
}

class UserJourneyDetector extends EventDetector[UserJourney] {
  import collection.mutable.Map
  val inflight: Map[User.ID, List[Event]] = Map[User.ID, List[Event]]()

  override def <<(m: Event): Boolean = m match {
    case c: Created =>
      inflight += (c.userID -> List(c))
      false

    case p: PickedUp =>
      inflight.get(p.userID)
        .map(c => c :+ p)
        .fold(false) { cp =>
          inflight += (p.userID -> cp)
          false
        }

    case d: DroppedOff =>
      inflight.get(d.userID)
        .flatMap{ cp =>
          inflight -= d.userID
          cp match {
            case c :: p :: Nil => Some(UserJourney(DateTime.now, c.asInstanceOf[Created], p.asInstanceOf[PickedUp], d))
            case _ => None
          }
        }
        .fold(false) { evt =>
          event = Some(evt)
          true
        }

    case _ => false
  }
}

class GroupByIntervalDetector[K](interval: Duration, key: Event => K) extends EventDetector[Event] {
  import collection.mutable.Map
  val reported: Map[K, Event] = Map[K, Event]()

  override def <<(m: Event): Boolean = (reported.get(key(m)), m) match {
    case (None, e) =>
      reported += (key(m) -> m)
      event = Some(m)
      true

    case (Some(prev), cur) if Duration(prev.createdAt, cur.createdAt).getMillis >= interval.getMillis =>
      reported += (key(cur) -> cur)
      event = Some(cur)
      true

    case (_, _) => false
  }
}


//class IntervalDetector[T, S, E](createEvent: (S,E) => T) extends EventDetector[T] {
//  import collection.mutable.Map
//  val inflight: Map[User.ID, S] = Map[User.ID, S]()
//
//  override def <<(m: Event): Boolean = m match {
//    case s: BookingEvent if s.isInstanceOf[S] =>
//      inflight += (s.userID -> s.asInstanceOf[S])
//      false
//
//    case e: BookingEvent if e.isInstanceOf[E] =>
//      inflight.get(e.userID)
//        .map(s => createEvent(s, e.asInstanceOf[E]))
//        .fold(false) { evt =>
//          inflight -= e.userID
//          event = Some(evt)
//          true
//        }
//
//    case _ => false
//  }
//}
//
//object IntervalDetector {
//  def userWait = new IntervalDetector[UserWait, Created, PickedUp]((c, p) => UserWait(c, p))
//  def userJourney = new IntervalDetector[UserJourney, PickedUp, DroppedOff]((p, d) => UserJourney(p, d))
//}
