package praktikum3

import akka.actor.typed.receptionist.ServiceKey
import praktikum3.ReplyDumper.{CommandReplyDumper, PrintSumTotalOrdersOfCustomer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.delivery.ConsumerController

import scala.collection.immutable.HashMap

object Finance {

  val financekey = ServiceKey[ConsumerController.Command[SaveCustomerAndPrice]]("Finance")

  def apply(mapCustomer: HashMap[Int, Int] = new HashMap()): Behavior[CommandFinance] = {
    Behaviors.setup { context =>

      context.log.info("Finance started")

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveCustomerAndPrice]](WrappedDelivery(_))

      val consumerController =
        context.spawn(ConsumerController(financekey), "consumerController")
      consumerController ! ConsumerController.Start(deliveryAdapter)


      Behaviors.receiveMessage {
        case WrappedDelivery(delivery) =>

          context.log.info("MESSAGE SAVE RECEIVED IN FINANCE")

          val currentSum = mapCustomer.getOrElse(delivery.message.customer.id, 0)
          val updatedCustomer = mapCustomer + (delivery.message.customer.id -> (currentSum + delivery.message.totalPrice))

          apply(updatedCustomer)

          delivery.confirmTo ! ConsumerController.Confirmed
          Behaviors.same

        case currentCustomer: PrintCustomerAndPrice =>
          context.log.info("MESSAGE PRINT RECEIVED IN FINANCE")


          currentCustomer.replyTo ! PrintSumTotalOrdersOfCustomer(currentCustomer.id, mapCustomer.get(currentCustomer.id))

          Behaviors.same

        case unknownMessage =>
          context.log.warn(s"Received unknown message in FINANCE: ${unknownMessage.getClass.getSimpleName}")
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

/*case currentOrder: SaveCustomerAndPrice =>

       val currentSum = mapCustomer.getOrElse(currentOrder.customer.id, 0)
       val updatedCustomer = mapCustomer + (currentOrder.customer.id -> (currentSum + currentOrder.totalPrice))

       apply(updatedCustomer)

       // Confirm message delivery after processing
        currentOrder.confirmTo ! ConsumerController.Confirmed

     // delivery.confirmTo ! ConsumerController.Confirmed*/