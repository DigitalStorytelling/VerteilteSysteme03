package praktikum3

import akka.actor.typed.receptionist.ServiceKey
import praktikum3.ReplyDumper.{CommandReplyDumper, PrintSumTotalOrdersOfCustomer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.delivery.ConsumerController

import scala.collection.immutable.HashMap

object Finance {

  // todo: check if right, why ConsumerController
  val financekey = ServiceKey[ConsumerController.Command[CommandFinance]]("Finance")

  def apply(mapCustomer: HashMap[Int, Int] = new HashMap()): Behavior[CommandFinance] = {
    Behaviors.setup { context =>

      //todo:
      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveCustomerAndPrice]](DeliveryFinance(_))
      val consumerController =
        context.spawn(ConsumerController(financekey), "consumerController")
      consumerController ! ConsumerController.Start(deliveryAdapter)


      Behaviors.receiveMessage {
        case currentOrder: SaveCustomerAndPrice =>

          val currentSum = mapCustomer.getOrElse(currentOrder.customer.id, 0)
          val updatedCustomer = mapCustomer + (currentOrder.customer.id -> (currentSum + currentOrder.totalPrice))

          apply(updatedCustomer)

          // delivery.confirmTo ! ConsumerController.Confirmed

        case currentCustomer: PrintCustomerAndPrice =>

          currentCustomer.replyTo ! PrintSumTotalOrdersOfCustomer(currentCustomer.id, mapCustomer.get(currentCustomer.id))

          Behaviors.same
      }
    }
  }

  sealed trait CommandFinance

  case class SaveCustomerAndPrice(customer: Customer, totalPrice: Int) extends CommandFinance
  case class PrintCustomerAndPrice(id: Int, replyTo: ActorRef[CommandReplyDumper]) extends CommandFinance

  //todo:
  case class DeliveryFinance(d: ConsumerController.Delivery[SaveCustomerAndPrice])
}
