package praktikum3

import praktikum3.Finance.{CommandFinance, PrintCustomerAndPrice, SaveCustomerAndPrice}
import praktikum3.Reader.{CommandReader, Next}
import praktikum3.Stock.{CommandStock, PrintStock, SaveItems}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderDispatcher {
  def apply(actorReader: ActorRef[CommandReader], actorFinance: ActorRef[CommandFinance], actorStock: ActorRef[CommandStock]): Behavior[CommandOrderDispatcher] =
    Behaviors.setup { (context) =>
      context.log.info("Order Dispatcher started")
      actorReader ! Next(context.self)

      Behaviors.receiveMessage {
        case currentAnswer: NextOrder =>
          actorReader ! Next(context.self)

          //an Finanzen weitergeben wie teuer die Bestellung insgesamt ist
          val totalSum: Int = currentAnswer.order.items.map(item => item.price).sum
          actorFinance ! SaveCustomerAndPrice(currentAnswer.order.customer, totalSum)

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

}
