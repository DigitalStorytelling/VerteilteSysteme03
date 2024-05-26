package praktikum3

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object ReplyDumper {

  def apply(): Behavior[CommandReplyDumper] = {
    Behaviors.setup { (context) =>
      context.log.info("Started ReplyDumper")

      Behaviors.same

      Behaviors.receiveMessage{
        // Finance
        case customer: PrintSumTotalOrdersOfCustomer =>

          val result = customer.balance match {
            case Some(id) => id
            case None => "unknown"
          }

          context.log.info(s"Balance of customer ${customer.id} is ${result}")

          Behaviors.same

        //Stock
        case stock: PrintTotalStockOfItem =>

          val result = stock.stock match {
            case Some(id) => id
            case None => "unknown"
          }

          context.log.info(s"Quantity of part ${stock.id} is ${result}")

          Behaviors.same
      }
    }
  }

  sealed trait CommandReplyDumper
  // Balance is either "unknown" or an Integer
  case class PrintSumTotalOrdersOfCustomer(id: Int, balance: Option[Int]) extends CommandReplyDumper
  case class PrintTotalStockOfItem(id: Int, stock: Option[Int]) extends CommandReplyDumper
}