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

  def apply(id: Long): Behavior[CommandFinance] =
    Behaviors.setup { context =>

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveCustomerAndPrice]](WrappedSaveCustomerAndPrice(_))

      context.system.receptionist ! Receptionist.Register(financeGuardianKey, context.self)

      val consumerController =
        context.spawn(ConsumerController(financekey), "consumerControllerFinance")

      consumerController ! ConsumerController.Start(deliveryAdapter)

      EventSourcedBehavior.withEnforcedReplies
          [CommandFinance, EventFinance, State](
            persistenceId = PersistenceId(id.toString, "finance-id"),
            emptyState = State(new HashMap[Int, Int]()),
            commandHandler = commandHandlerFinance,
            eventHandler = eventHandlerFinance
          )
        .withRetention(
          RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 5)
        )
    }

  val eventHandlerFinance: (State, EventFinance) => State = { (state, event) =>

    event match {
      case SaveCustomerAndPriceEvent(id, totalPrice) => {
        val currentSum = state.mapCustomer.getOrElse(id, 0)
        state.copy(state.mapCustomer + (id -> (currentSum + totalPrice)))
      }
    }
  }

  val commandHandlerFinance: (State, CommandFinance) => ReplyEffect[EventFinance, State] = {
    (state, command) =>
      command match {
        case WrappedSaveCustomerAndPrice(delivery) =>
          Effect.persist(SaveCustomerAndPriceEvent(delivery.message.customer.id, delivery.message.totalPrice))
            .thenReply(delivery.confirmTo)(s => ConsumerController.Confirmed)

        // No Persist necessary, only Print
        case PrintCustomerAndPrice(id, replyTo) =>
          Effect.reply(replyTo)(PrintSumTotalOrdersOfCustomer(id, state.mapCustomer.get(id)))
      }
  }

  final case class State(@JsonDeserialize(keyAs = classOf[Int]) mapCustomer: HashMap[Int, Int])

  sealed trait CommandFinance

  case class SaveCustomerAndPrice(customer: Customer, totalPrice: Int) extends CommandFinance

  case class PrintCustomerAndPrice(id: Int, replyTo: ActorRef[CommandReplyDumper]) extends CommandFinance

  case class WrappedSaveCustomerAndPrice(d: ConsumerController.Delivery[SaveCustomerAndPrice]) extends CommandFinance

  sealed trait EventFinance

  case class SaveCustomerAndPriceEvent(id: Int, totalPrice: Int) extends EventFinance

}