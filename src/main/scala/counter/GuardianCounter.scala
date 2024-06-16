package counter

import akka.actor.typed.{Behavior}
import akka.actor.typed.scaladsl.Behaviors
import counter.Counter.{IncreaseCommand}

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
