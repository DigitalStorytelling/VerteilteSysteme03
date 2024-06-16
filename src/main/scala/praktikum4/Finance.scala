package praktikum4

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import praktikum4.ReplyDumper.{CommandReplyDumper, PrintSumTotalOrdersOfCustomer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.delivery.ConsumerController
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import scala.collection.immutable.HashMap

object Finance {

  val financekey = ServiceKey[ConsumerController.Command[SaveCustomerAndPrice]]("Finance")
  val financeGuardianKey: ServiceKey[CommandFinance] = ServiceKey[CommandFinance]("FinanceGuardian")

  def apply(id: Long, mapCustomer: HashMap[Int, Int] = new HashMap()): Behavior[CommandFinance] =
    Behaviors.setup { context =>

      context.log.info("Finance")

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveCustomerAndPrice]](WrappedSaveCustomerAndPrice(_))

      if (mapCustomer.isEmpty) {
        context.system.receptionist ! Receptionist.Register(financeGuardianKey, context.self)

        val consumerController =
          context.spawn(ConsumerController(financekey), "consumerControllerFinance")

        consumerController ! ConsumerController.Start(deliveryAdapter)
      }

      EventSourcedBehavior.withEnforcedReplies
          [CommandFinance, EventFinance, State](
            persistenceId = PersistenceId(id.toString, "finance-id"),
            emptyState = State(new HashMap()),
            commandHandler = commandHandlerFinance,
            eventHandler = eventHandlerFinance
          )
        .withRetention(
          RetentionCriteria.snapshotEvery(numberOfEvents = 1, keepNSnapshots = 5)
        )
    }


  val eventHandlerFinance: (State, EventFinance) => State = { (state, event) =>

    event match {
      case SaveCustomerAndPriceEvent(customer, totalPrice) => {
        val currentSum = state.mapCustomer.getOrElse(customer.id, 0)
        val updatedCustomer = state.mapCustomer + (customer.id -> (currentSum + totalPrice))
        state.copy(updatedCustomer)
      }
    }
  }

  val commandHandlerFinance: (State, CommandFinance) => ReplyEffect[EventFinance, State] = {
    (state, command) =>
      command match {
        case WrappedSaveCustomerAndPrice(delivery) =>
          Effect.persist(SaveCustomerAndPriceEvent(delivery.message.customer, delivery.message.totalPrice))
            .thenReply(delivery.confirmTo)(s => ConsumerController.Confirmed)

        // No Persist necessary, only Print
        case PrintCustomerAndPrice(id, replyTo) =>
          Effect.reply(replyTo)(PrintSumTotalOrdersOfCustomer(id, state.mapCustomer.get(id)))
      }
  }

  final case class State(@JsonDeserialize(contentAs = classOf[Int], keyAs = classOf[Int]) mapCustomer: HashMap[Int, Int])
  sealed trait CommandFinance
  case class SaveCustomerAndPrice(customer: Customer, totalPrice: Int) extends CommandFinance
  case class PrintCustomerAndPrice(id: Int, replyTo: ActorRef[CommandReplyDumper]) extends CommandFinance
  case class WrappedSaveCustomerAndPrice(d: ConsumerController.Delivery[SaveCustomerAndPrice]) extends CommandFinance

  sealed trait EventFinance
  case class SaveCustomerAndPriceEvent(customer: Customer, totalPrice: Int) extends EventFinance

}

//todo: remove
/*
    Behaviors.receiveMessage {
    case WrappedSaveCustomerAndPrice(delivery) =>

     val currentSum = mapCustomer.getOrElse(delivery.message.customer.id, 0)
     val updatedCustomer = mapCustomer + (delivery.message.customer.id -> (currentSum + delivery.message.totalPrice))

     delivery.confirmTo ! ConsumerController.Confirmed
     apply(id, updatedCustomer)

  case currentCustomer: PrintCustomerAndPrice =>

    currentCustomer.replyTo ! PrintSumTotalOrdersOfCustomer(currentCustomer.id, mapCustomer.get(currentCustomer.id))
    Behaviors.same

}
*/