package praktikum3

import akka.actor.typed.delivery.WorkPullingProducerController
import praktikum3.Finance.{CommandFinance, PrintCustomerAndPrice}
import praktikum3.Reader.{CommandReader, Next}
import praktikum3.Stock.{CommandStock, PrintStock}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}

object OrderDispatcher {
  def apply(actorReader: ActorRef[CommandReader], actorFinance: ActorRef[CommandFinance], actorStock: ActorRef[CommandStock]): Behavior[CommandOrderDispatcher] = {
    Behaviors.setup { context =>

      context.log.info("Order Dispatcher started")

      val producerControllerFinance = context.spawn(
        WorkPullingProducerController(
          producerId = "workManagerFinance",
          workerServiceKey = Finance.financekey,
          //todo: Some(durableQueue)
          durableQueueBehavior = None),
        "producerControllerFinance")

      val producerControllerStock = context.spawn(
        WorkPullingProducerController(
          producerId = "workManagerStock",
          workerServiceKey = Stock.stockkey,
          //todo: Some(durableQueue)
          durableQueueBehavior = None),
        "producerControllerStock"
      )

      val requestNextAdapterFinance = context.messageAdapter[WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]](WrappedRequestNextFinance)
      val requestNextAdapterStock = context.messageAdapter[WorkPullingProducerController.RequestNext[Stock.SaveItems]](WrappedRequestNextStock)

      // Start work-pulling
      producerControllerFinance ! WorkPullingProducerController.Start(requestNextAdapterFinance)
      producerControllerStock ! WorkPullingProducerController.Start(requestNextAdapterStock)

      // Start reading and start work-pulling
      actorReader ! Next(context.self)

      Behaviors.withStash(2000) { stashBuffer: StashBuffer[CommandOrderDispatcher] =>
        new OrderDispatcher(context, stashBuffer, actorReader, actorFinance, actorStock).waitForNext()
      }
    }
  }

  sealed trait CommandOrderDispatcher

  final case class NextOrder(order: Order) extends CommandOrderDispatcher

  final case object Empty extends CommandOrderDispatcher

  private case class WrappedRequestNextFinance(r: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]) extends CommandOrderDispatcher

  private case class WrappedRequestNextStock(r: WorkPullingProducerController.RequestNext[Stock.SaveItems]) extends CommandOrderDispatcher

}

final class OrderDispatcher(context: ActorContext[OrderDispatcher.CommandOrderDispatcher],
                            stashBuffer: StashBuffer[OrderDispatcher.CommandOrderDispatcher],
                            actorReader: ActorRef[CommandReader],
                            actorFinance: ActorRef[CommandFinance],
                            actorStock: ActorRef[CommandStock]) {

  import OrderDispatcher._

  val actorReplyDumper: ActorRef[ReplyDumper.CommandReplyDumper] = context.spawnAnonymous(ReplyDumper())

  private def waitForNext(): Behavior[CommandOrderDispatcher] = {
    Behaviors.receiveMessagePartial {

      case WrappedRequestNextFinance(next) =>
        stashBuffer.unstashAll(activeFinance(next))

      case WrappedRequestNextStock(next) =>
        stashBuffer.unstashAll(activeStock(next))

      case order: NextOrder =>
        if (stashBuffer.isFull) {
          context.log.warn("Too many Save Requests.")
          Behaviors.same
        } else {
          stashBuffer.stash(order)
          Behaviors.same
        }
      case unknownMessage =>
        context.log.warn(s"Received unknown message in waitNext() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same
    }
  }

  private def activeFinance(financeNext: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]): Behavior[CommandOrderDispatcher] = {
    Behaviors.receiveMessagePartial {
      case NextOrder(order) =>
        actorReader ! Next(context.self)

        val totalSum: Int = order.items.map(item => item.price).sum
        financeNext.sendNextTo ! Finance.SaveCustomerAndPrice(order.customer, totalSum)

        waitForNext()

      case Empty =>
        actorFinance ! PrintCustomerAndPrice(1000000, actorReplyDumper) // answer: unknown
        actorFinance ! PrintCustomerAndPrice(19448, actorReplyDumper) // answer: 868923685

        Behaviors.same

      case unknownMessage =>
        context.log.warn(s"Received unknown message in financeStock() method: ${unknownMessage.getClass.getSimpleName}")
        waitForNext()
    }
  }

  private def activeStock(stockNext: WorkPullingProducerController.RequestNext[Stock.SaveItems]): Behavior[CommandOrderDispatcher] = {
    Behaviors.receiveMessagePartial {
      case NextOrder(order) =>
        actorReader ! Next(context.self)

        stockNext.sendNextTo ! Stock.SaveItems(order.items.map(item => (item.id, item.quantity)).toMap)
        waitForNext()

      case Empty =>
        actorStock ! PrintStock(1000000, actorReplyDumper) // answer: unknown
        actorStock ! PrintStock(65565, actorReplyDumper) // answer: 608

        Behaviors.same

      case _: WrappedRequestNextStock =>
        throw new IllegalStateException("Unexpected RequestNextStock")

      case unknownMessage =>
        context.log.warn(s"Received unknown message in activeStock() method: ${unknownMessage.getClass.getSimpleName}")
        waitForNext()
    }
  }
}