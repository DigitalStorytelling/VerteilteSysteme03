package praktikum3

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import praktikum3.ReplyDumper.{CommandReplyDumper, PrintSumTotalOrdersOfCustomer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.delivery.ConsumerController

import scala.collection.immutable.HashMap

object Finance  {

  val financekey = ServiceKey[ConsumerController.Command[SaveCustomerAndPrice]]("Finance")

  val financeGuardianKey: ServiceKey[CommandFinance] = ServiceKey[CommandFinance]("FinanceGuardian")

  def apply(mapCustomer: HashMap[Int, Int] = new HashMap()): Behavior[CommandFinance] = {
    Behaviors.setup { context =>

      context.log.info("Finance")

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveCustomerAndPrice]](WrappedSaveCustomerAndPrice(_))

      if(mapCustomer.isEmpty) {
        context.system.receptionist ! Receptionist.Register(financeGuardianKey, context.self)

        val consumerController =
          context.spawn(ConsumerController(financekey), "consumerControllerFinance")
          consumerController ! ConsumerController.Start(deliveryAdapter)
      }

      Behaviors.receiveMessage {
        case WrappedSaveCustomerAndPrice(delivery) =>

          val currentSum = mapCustomer.getOrElse(delivery.message.customer.id, 0)
          val updatedCustomer = mapCustomer + (delivery.message.customer.id -> (currentSum + delivery.message.totalPrice))

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
  case class WrappedSaveCustomerAndPrice(d: ConsumerController.Delivery[SaveCustomerAndPrice]) extends CommandFinance
}