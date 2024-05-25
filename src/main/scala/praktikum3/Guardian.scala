package praktikum3

import praktikum3.Finance.CommandFinance
import praktikum3.Stock.CommandStock
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors

object Guardian {

  def apply(actorStock: ActorRef[CommandStock] = null, actorFinance: ActorRef[CommandFinance] = null, guardianUpOnlyOnce: Boolean = true): Behavior[Receptionist.Listing] =
    Behaviors.setup[Receptionist.Listing] { context =>

      val role = System.getenv("role")
      context.log.info(s"Guardian started as $role.")

      role match {
        case "dispatcher" =>
          context.log.info("Dispatcher/Reader/ReplyDumper Role detected")
          context.system.receptionist ! Receptionist.Subscribe(Finance.FinanceGuardianKey, context.self)
          context.system.receptionist ! Receptionist.Subscribe(Stock.StockGuardianKey, context.self)

        case "stock" =>
          context.log.info("Stock Role detected")
          context.spawnAnonymous(Stock())

        case "finance" =>
          context.log.info("Finance Role detected")
          context.spawnAnonymous(Finance())

        case _ =>
          context.log.info("Unknown starting role")
      }

      // Wait before start Reader and Orderdispatcher
      Behaviors.receiveMessagePartial {

        case Finance.FinanceGuardianKey.Listing(financeListings) if financeListings.nonEmpty =>
          context.log.info("Finance available")

          if(actorStock != null){
            val actorReader = context.spawnAnonymous(Reader())
            context.log.info("Start Reader")
            context.spawnAnonymous(OrderDispatcher(actorReader, financeListings.head, actorStock))
            Behaviors.same
          } else if (guardianUpOnlyOnce) {
            apply(actorStock, financeListings.head, guardianUpOnlyOnce = false)
          } else {
            Behaviors.same
          }

        case Finance.FinanceGuardianKey.Listing(financeListings) if financeListings.isEmpty =>
          context.log.info("No Finance available")
          Behaviors.same

        case Stock.StockGuardianKey.Listing(stockListings) if stockListings.nonEmpty =>
          context.log.info("Stock available")

          if(actorFinance != null) {
            val actorReader = context.spawnAnonymous(Reader())
            context.log.info("Start Reader")
            context.spawnAnonymous(OrderDispatcher(actorReader, actorFinance, stockListings.head))
            Behaviors.same
          } else if (guardianUpOnlyOnce) {
            apply(stockListings.head, actorFinance, guardianUpOnlyOnce = false)
          } else {
            Behaviors.same
          }

        case Stock.StockGuardianKey.Listing(stockListings) if stockListings.isEmpty =>
          context.log.info("No Stock available")
          Behaviors.same
      }

    }.narrow
}
