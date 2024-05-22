package praktikum3
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Guardian {
  def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    context.log.info("Guardian started")

    val actorReader = context.spawn(Reader(), "Reader")
    val actorFinance = context.spawn(Finance(), "Finance")
    val actorStock = context.spawn(Stock(), "Stock")
    //val actorReplyDumper = context.spawn(ReplyDumper(), "ReplyDumper")

    context.spawn(OrderDispatcher(actorReader, actorFinance, actorStock), "Dispatcher")

    Behaviors.same
  }
}