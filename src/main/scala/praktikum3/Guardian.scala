package praktikum3

import praktikum3.Finance.CommandFinance
import praktikum3.Stock.{CommandStock, StockGuardianKey}
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import praktikum3.Reader.CommandReader

object Guardian {
  def apply(actorStock: ActorRef[CommandStock] = null, actorFinance: ActorRef[CommandFinance] = null, guardianUpOnlyOnce: Boolean = true, readerUp:Boolean = false): Behavior[Receptionist.Listing] =
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

          if(actorStock != null && !readerUp ){
            val actorReader = context.spawnAnonymous(Reader())
            context.log.info("Start Reader")
            context.spawnAnonymous(OrderDispatcher(actorReader, financeListings.head, actorStock))
            apply(actorStock, financeListings.head, guardianUpOnlyOnce = false, readerUp = true)
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

          if(actorFinance != null && !readerUp) {
            val actorReader = context.spawnAnonymous(Reader())
            context.log.info("Start Reader")
            context.spawnAnonymous(OrderDispatcher(actorReader, actorFinance, stockListings.head))
            apply(stockListings.head, actorFinance, guardianUpOnlyOnce = false, readerUp = true)
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

  def startReader(stockActor: ActorRef[CommandStock], financeActor: ActorRef[CommandFinance]): Behavior[CommandReader]{

  }
}
