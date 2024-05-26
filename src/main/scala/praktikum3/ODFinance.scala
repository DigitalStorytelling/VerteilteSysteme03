package praktikum3

import akka.actor.typed.delivery.WorkPullingProducerController
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import praktikum3.Finance.{CommandFinance, PrintCustomerAndPrice}
import praktikum3.Reader.{CommandReader, Next}
import praktikum3.Stock.{CommandStock, PrintStock}

final class ODFinance(context: ActorContext[OrderDispatcher.CommandOrderDispatcher],
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
      /*case unknownMessage =>
        context.log.warn(s"Received unknown message in waitNextStock() method: ${unknownMessage.getClass.getSimpleName}")
        Behaviors.same*/
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

    }
  }

}
