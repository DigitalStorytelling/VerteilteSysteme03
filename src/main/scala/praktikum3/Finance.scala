package praktikum3

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import praktikum3.ReplyDumper.{CommandReplyDumper, PrintSumTotalOrdersOfCustomer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.delivery.ConsumerController

import scala.collection.immutable.HashMap

object Finance {

  val financekey = ServiceKey[ConsumerController.Command[SaveCustomerAndPrice]]("Finance")

  def apply(mapCustomer: HashMap[Int, Int] = new HashMap()): Behavior[CommandFinance] = {
    Behaviors.setup { context =>

      context.log.info("Finance")

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveCustomerAndPrice]](WrappedDelivery(_))

      if(mapCustomer.isEmpty) {
        val consumerController =
          context.spawn(ConsumerController(financekey), "consumerControllerFinance")
          consumerController ! ConsumerController.Start(deliveryAdapter)
      }

      Behaviors.receiveMessage {
        case WrappedDelivery(delivery) =>

          val currentSum = mapCustomer.getOrElse(delivery.message.customer.id, 0)
          val updatedCustomer = mapCustomer + (delivery.message.customer.id -> (currentSum + delivery.message.totalPrice))

          //todo: apply after confirm!
          delivery.confirmTo ! ConsumerController.Confirmed
          apply(updatedCustomer)

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
  case class WrappedDelivery(d: ConsumerController.Delivery[SaveCustomerAndPrice]) extends CommandFinance
}