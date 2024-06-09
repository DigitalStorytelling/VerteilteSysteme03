package counter

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import counter.GuardianCounter.{GuardianCommand, Print}

object Counter {

  final case class State(counter: Int)
  def apply(id: Long): Behavior[CounterCommand] =

    Behaviors.setup[CounterCommand] { context =>
      context.log.info("Counter started")

      //todo: withEnforcedReplies weglassen?
  EventSourcedBehavior
      [CounterCommand, Event, State](
      persistenceId = PersistenceId(id.toString, "counter-id"),
      emptyState = State(0),
      commandHandler = commandHandlerCounter,
      eventHandler = eventHandlerCounter
    )
      .withRetention(
        RetentionCriteria.snapshotEvery(numberOfEvents = 1, keepNSnapshots = 5)
      )
  }


  val eventHandlerCounter: (State, Event) => State = { (state, event) =>
    event match {
      case IncreaseCounterEvent() => {
        //todo:
        println("Counter is " + state)
        state.copy(state.counter + 1)
      }
    }
  }

  val commandHandlerCounter: (State, CounterCommand) => Effect[Event, State] = {
    (state, command) =>
      command match {
        case IncreaseCommand(replyTo) =>
          Effect.persist(IncreaseCounterEvent())
            .thenReply(replyTo) { _ =>
              StatusReply.Ack
            }
      }
  }

  sealed trait CounterCommand
  case class IncreaseCommand(replyTo: ActorRef[StatusReply[Done]]) extends CounterCommand


  sealed trait Event
  final case class IncreaseCounterEvent() extends Event

}
