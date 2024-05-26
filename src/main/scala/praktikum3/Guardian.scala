package praktikum3

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import praktikum3.Finance.CommandFinance
import praktikum3.Stock.CommandStock

object Guardian {
  def apply(actorStock: ActorRef[CommandStock] = null, actorFinance: ActorRef[CommandFinance] = null, guardianUpOnlyOnce: Boolean = true, readerUp: Boolean = false): Behavior[Receptionist.Listing] =
    Behaviors.setup[Receptionist.Listing] { context =>

      val role = System.getenv("role")
      context.log.info(s"Guardian started as $role.")

      role match {
        case "dispatcher" =>
          context.log.info("Dispatcher/Reader/ReplyDumper Role detected")
          context.system.receptionist ! Receptionist.Subscribe(Finance.financeGuardianKey, context.self)
          context.system.receptionist ! Receptionist.Subscribe(Stock.stockGuardianKey, context.self)

        case "stock" =>
          context.log.info("Stock Role detected")
          context.spawnAnonymous(Stock())

        case "finance" =>
          context.log.info("Finance Role detected")
          context.spawnAnonymous(Finance())

        case _ =>
          context.log.info("Unknown starting role")
      }

      Behaviors.receiveMessagePartial {

        case Finance.financeGuardianKey.Listing(financeListings) if financeListings.nonEmpty =>
          context.log.info("Finance available")

          checkStartReader(context, actorStock, financeListings.head, guardianUpOnlyOnce, readerUp)

        case Finance.financeGuardianKey.Listing(financeListings) if financeListings.isEmpty =>
          context.log.info("No Finance available")
          Behaviors.same

        case Stock.stockGuardianKey.Listing(stockListings) if stockListings.nonEmpty =>
          context.log.info("Stock available")

          checkStartReader(context, stockListings.head, actorFinance, guardianUpOnlyOnce, readerUp)

        case Stock.stockGuardianKey.Listing(stockListings) if stockListings.isEmpty =>
          context.log.info("No Stock available")
          Behaviors.same
      }

    }.narrow

  // will make the Guardian start 3 times (because of apply and giving information to the next)
  // will start the reader and replyDumper only once!
  def checkStartReader(context: ActorContext[Receptionist.Listing], stockActor: ActorRef[CommandStock], financeActor: ActorRef[CommandFinance], guardianUpOnlyOnce: Boolean, readerUp: Boolean): Behavior[Receptionist.Listing] = {

    // all actors presents
    if (stockActor != null && financeActor != null && !readerUp) {
      val actorReader = context.spawnAnonymous(Reader())
      context.log.info("Start Reader")
      context.spawnAnonymous(OrderDispatcher(actorReader, financeActor, stockActor))
      apply(stockActor, financeActor, guardianUpOnlyOnce = false, readerUp = true)

      // only Finance or Stock is found. Needs another run to have both
      // cant use readerUp here
    } else if (guardianUpOnlyOnce) {
      apply(stockActor, financeActor, guardianUpOnlyOnce = false)

    } else {
      Behaviors.same
    }
  }
}