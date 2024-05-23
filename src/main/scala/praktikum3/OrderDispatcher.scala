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

      // Work-Pulling Finance
      val requestNextAdapterFinance = context.messageAdapter[WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]](WrappedRequestNextFinance)
      val requestNextAdapterStock = context.messageAdapter[WorkPullingProducerController.RequestNext[Stock.SaveItems]](WrappedRequestNextStock)

      val producerControllerFinance = context.spawn(
        WorkPullingProducerController(
          producerId = "workManagerFinance",
          workerServiceKey = Finance.financekey,
          //todo: Some(durableQueue)
          durableQueueBehavior = None),
        "producerController")

      val producerControllerStock = context.spawn(
        WorkPullingProducerController(
          producerId = "workManagerStock",
          workerServiceKey = Stock.stockkey,
          //todo: Some(durableQueue)
          durableQueueBehavior = None),
        "producerController"
      )

      // Start reading and start work-pulling
      actorReader ! Next(context.self)
      producerControllerFinance ! WorkPullingProducerController.Start(requestNextAdapterFinance)
      producerControllerStock ! WorkPullingProducerController.Start(requestNextAdapterStock)

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

  private def waitForNext(): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial {

      case WrappedRequestNextFinance(next) =>
        stashBuffer.unstashAll(activeFinance(next))

      case WrappedRequestNextStock(next) =>
        stashBuffer.unstashAll(activeStock(next))

      case order: NextOrder =>
        if (stashBuffer.isFull) {
          context.log.warn("Too many Finance/Stock Save Requests.")
          Behaviors.same

        } else {
          stashBuffer.stash(order)
          Behaviors.same
        }

      case unknownMessage =>
        context.log.warn(s"Received unknown message in waitForNext() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same
    }
  }

  private def activeFinance(next: WorkPullingProducerController.RequestNext[Finance.SaveCustomerAndPrice]): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial {

      case NextOrder(order) =>
        actorReader ! Next(context.self)

        val totalSum: Int = order.items.map(item => item.price).sum
        next.sendNextTo ! Finance.SaveCustomerAndPrice(order.customer, totalSum)

        waitForNext()

      case Empty =>

        val actorReplyDumper = context.spawnAnonymous(ReplyDumper())

        actorFinance ! PrintCustomerAndPrice(1000000, actorReplyDumper) // answer: unknown
        actorFinance ! PrintCustomerAndPrice(19448, actorReplyDumper) // answer: 868923685

        Behaviors.stopped

      case unknownMessage =>
        context.log.warn(s"Received unknown message in active() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same
    }
  }
  private def activeStock(next: WorkPullingProducerController.RequestNext[Stock.SaveItems]): Behavior[CommandOrderDispatcher] = {

    Behaviors.receiveMessagePartial {

      case NextOrder(order) =>

        actorReader ! Next(context.self)

        val itemQuantities: Map[Int, Int] = order.items.map(item => (item.id, item.quantity)).toMap
        next.sendNextTo ! Stock.SaveItems(itemQuantities)

        waitForNext()

      case Empty =>

        val actorReplyDumper = context.spawnAnonymous(ReplyDumper())

        actorStock ! PrintStock(1000000, actorReplyDumper) // answer: unknown
        actorStock ! PrintStock(65565, actorReplyDumper) //answer: 608

        Behaviors.stopped

      case unknownMessage =>
        context.log.warn(s"Received unknown message in active() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same
    }
  }
}