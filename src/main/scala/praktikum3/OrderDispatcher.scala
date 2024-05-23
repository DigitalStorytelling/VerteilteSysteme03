package praktikum3

import akka.actor.typed.delivery.WorkPullingProducerController
import praktikum3.Finance.{CommandFinance, PrintCustomerAndPrice}
import praktikum3.Reader.{CommandReader, Next}
import praktikum3.Stock.{CommandStock, PrintStock, SaveItems}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.delivery.EventSourcedProducerQueue
import akka.persistence.typed.PersistenceId

object OrderDispatcher {

  def apply(actorReader: ActorRef[CommandReader], actorFinance: ActorRef[CommandFinance], actorStock: ActorRef[CommandStock]): Behavior[CommandOrderDispatcher] = {
    Behaviors.setup { context =>

      context.log.info("Order Dispatcher started")

      // Work-Pulling Finance
      val requestNextAdapter = context.messageAdapter[WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]](WrappedRequestNext)

      val producerController = context.spawn(
        WorkPullingProducerController(
          producerId = "workManager",
          workerServiceKey = Finance.financekey,
          //todo: Some(durableQueue)
          durableQueueBehavior = None),
        "producerController")

      // Start reading and start work-pulling
      actorReader ! Next(context.self)
      producerController ! WorkPullingProducerController.Start(requestNextAdapter)


      Behaviors.withStash(2000) { stashBuffer: StashBuffer[CommandOrderDispatcher] =>
        new OrderDispatcher(context, stashBuffer, actorReader).waitForNext()
      }

      /*Behaviors.receiveMessage {
        case currentAnswer: NextOrder =>
          actorReader ! Next(context.self)

          // An Stock Item List weiterreichen
          val itemQuantities: Map[Int, Int] = currentAnswer.order.items.map(item => (item.id, item.quantity)).toMap
          actorStock ! SaveItems(itemQuantities)

          Behaviors.same

        case Empty =>

          val actorReplyDumper = context.spawnAnonymous(ReplyDumper())

          //actorStock ! PrintStock(1000000, actorReplyDumper) // answer: unknown
          //actorStock ! PrintStock(65565, actorReplyDumper) //answer: 608

          actorFinance ! PrintCustomerAndPrice(1000000, actorReplyDumper) // answer: unknown
          actorFinance ! PrintCustomerAndPrice(19448, actorReplyDumper) // answer: 868923685

          Behaviors.stopped

        case WrappedRequestNext(next) =>
          context.log.info("Got WrappedRequestNext")
          Behaviors.same

        case unknownMessage =>
          context.log.warn(s"Received unknown message in Main Dispatcher reiceive: ${unknownMessage.getClass.getSimpleName}")
          Behaviors.same
      }*/

    }
  }


sealed trait CommandOrderDispatcher

final case class NextOrder(order: Order) extends CommandOrderDispatcher

final case object Empty extends CommandOrderDispatcher

private case class WrappedRequestNext(r: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]) extends CommandOrderDispatcher

}

final class OrderDispatcher(context: ActorContext[OrderDispatcher.CommandOrderDispatcher],
                            stashBuffer: StashBuffer[OrderDispatcher.CommandOrderDispatcher],
                            actorReader: ActorRef[CommandReader]) {

  import OrderDispatcher._

  private def waitForNext(): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial {
      case WrappedRequestNext(next) =>
        stashBuffer.unstashAll(active(next))

      case order: NextOrder =>
        context.log.info("Got NextOrder")

        if (stashBuffer.isFull) {
          context.log.warn("Too many Finance/Stock Save Requests.")
          Behaviors.same
        } else {
          context.log.info("MESSAGE WAS STASHED")
          stashBuffer.stash(order)
          Behaviors.same
        }

      case unknownMessage =>
        context.log.warn(s"Received unknown message in waitForNext() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same
    }
  }

  private def active(next: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial {

      case NextOrder(order) =>

        actorReader ! Next(context.self)

        val totalSum: Int = order.items.map(item => item.price).sum
        next.sendNextTo ! Finance.SaveCustomerAndPrice(order.customer, totalSum)
        waitForNext()

      case unknownMessage =>
        context.log.warn(s"Received unknown message in active() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same

    }
  }
}