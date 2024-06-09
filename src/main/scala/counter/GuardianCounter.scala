package counter

import akka.Done
import akka.actor.typed.{Behavior, scaladsl}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import counter.Counter.{CounterCommand, IncreaseCommand}
import org.agrona.concurrent.status.CountersReader.CounterConsumer

object GuardianCounter{
  def apply(): Behavior[GuardianCommand] = {

    Behaviors.setup[GuardianCommand] { context =>
      context.log.info("Guardian started")

      val counter = context.spawn(Counter(0), "Counter")
      counter ! IncreaseCommand(context.self)
      counter ! IncreaseCommand(context.self)
      counter ! IncreaseCommand(context.self)
      counter ! IncreaseCommand(context.self)
      counter ! IncreaseCommand(context.self)
      counter ! IncreaseCommand(context.self)
      counter ! IncreaseCommand(context.self)

      Behaviors.receiveMessage{
        case currentNumber: Print =>
          context.log.info(s"Counter: ${currentNumber.counter}")
          Behaviors.same
      }
    }
  }
  trait GuardianCommand
  final case class Print(counter: Int) extends GuardianCommand
}
