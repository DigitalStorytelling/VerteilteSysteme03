package praktikum3

import akka.actor.typed.delivery.WorkPullingProducerController
import praktikum3.Finance.{CommandFinance, PrintCustomerAndPrice}
import praktikum3.Reader.{CommandReader, Next}
import praktikum3.Stock.{CommandStock, PrintStock, SaveItems}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.StashBuffer


object OrderDispatcher {
  def apply(actorReader: ActorRef[CommandReader], actorFinance: ActorRef[CommandFinance], actorStock: ActorRef[CommandStock]): Behavior[CommandOrderDispatcher] =
    Behaviors.setup { (context) =>
      context.log.info("Order Dispatcher started")
      actorReader ! Next(context.self)

     val requestNextAdapter = context.messageAdapter[WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]](WrappedRequestNext)


      val producerController = context.spawn(
        WorkPullingProducerController(
          producerId = "workManager",
          workerServiceKey = Finance.financekey,
          //todo: ?
          durableQueueBehavior = None),
        "producerController")

      producerController ! WorkPullingProducerController.Start(requestNextAdapter)

      Behaviors.withStash(2000) { stashBuffer : StashBuffer[CommandOrderDispatcher] =>
        new OrderDispatcher(context, stashBuffer).waitForNext()
      }

      Behaviors.receiveMessage {
        case currentAnswer: NextOrder =>
          actorReader ! Next(context.self)

          // An Stock Item List weiterreichen
          val itemQuantities: Map[Int, Int] = currentAnswer.order.items.map(item => (item.id, item.quantity)).toMap
          actorStock ! SaveItems(itemQuantities)

          Behaviors.same

        case Empty =>

          val actorReplyDumper = context.spawnAnonymous(ReplyDumper())

          actorStock ! PrintStock(1000000, actorReplyDumper) // answer: unknown
          actorStock ! PrintStock(65565, actorReplyDumper) //answer: 608

          actorFinance ! PrintCustomerAndPrice(1000000, actorReplyDumper) // answer: unknown
          actorFinance ! PrintCustomerAndPrice(19448, actorReplyDumper) // answer: 868923685

          Behaviors.stopped
      }
    }

  sealed trait CommandOrderDispatcher
  case class NextOrder(order: Order) extends CommandOrderDispatcher
  case object Empty extends CommandOrderDispatcher

  case class WrappedRequestNext(r: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice])  extends CommandOrderDispatcher

}

final class OrderDispatcher(context: ActorContext[OrderDispatcher.CommandOrderDispatcher],
                            stashBuffer: StashBuffer[OrderDispatcher.CommandOrderDispatcher]) {

  import OrderDispatcher._

  private def waitForNext(): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial {

      case WrappedRequestNext(next) =>
        stashBuffer.unstashAll(active(next))

      case order: NextOrder =>
        if (stashBuffer.isFull) {
          context.log.warn("Too many Finance/Stock Save Requests.")
          Behaviors.same
        } else {
          stashBuffer.stash(order)
          Behaviors.same
        }
    }
  }

  private def active(next: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial{

      case order: NextOrder =>

        val totalSum: Int = order.order.items.map(item => item.price).sum

        // next.sendNextTo ! ImageConverter.ConversionJob(resultId, from, to, image)
        next.sendNextTo ! Finance.SaveCustomerAndPrice(order.order.customer, totalSum)
        waitForNext()

      case _: WrappedRequestNext =>
        throw new IllegalStateException("Unexpected RequestNext")

    }
  }
}