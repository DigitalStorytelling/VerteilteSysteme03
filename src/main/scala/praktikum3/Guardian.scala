package praktikum3

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import praktikum3.Finance.CommandFinance
import praktikum3.Stock.CommandStock

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

          checkStartReader(context, actorStock, financeListings.head, guardianUpOnlyOnce, readerUp)

        case Finance.FinanceGuardianKey.Listing(financeListings) if financeListings.isEmpty =>
          context.log.info("No Finance available")
          Behaviors.same

        case Stock.StockGuardianKey.Listing(stockListings) if stockListings.nonEmpty =>
          context.log.info("Stock available")

          checkStartReader(context, stockListings.head, actorFinance, guardianUpOnlyOnce, readerUp)

        case Stock.StockGuardianKey.Listing(stockListings) if stockListings.isEmpty =>
          context.log.info("No Stock available")
          Behaviors.same
      }

    }.narrow

  def checkStartReader(context:  ActorContext[Receptionist.Listing], stockActor: ActorRef[CommandStock], financeActor: ActorRef[CommandFinance], guardianUpOnlyOnce:Boolean, readerUp:Boolean): Behavior[Receptionist.Listing] = {

    if(stockActor != null && financeActor != null && !readerUp) {
      val actorReader = context.spawnAnonymous(Reader())
      context.log.info("Start Reader")
      context.spawnAnonymous(OrderDispatcher(actorReader, financeActor, stockActor))
      apply(stockActor, financeActor, guardianUpOnlyOnce = false, readerUp = true)
    } else if (guardianUpOnlyOnce){
      // only Finance or Stock is found. Needs another run to have both
      apply(stockActor, financeActor, guardianUpOnlyOnce = false)
    }  else {
      Behaviors.same
    }

  }
}
