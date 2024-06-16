package praktikum4

import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import praktikum4.ReplyDumper.{CommandReplyDumper, PrintTotalStockOfItem}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable.HashMap

object Stock  {

  val stockkey = ServiceKey[ConsumerController.Command[SaveItems]]("Stock")
  val stockGuardianKey: ServiceKey[CommandStock] = ServiceKey[CommandStock]("StockGuardian")

  def apply(mapStock: HashMap[Int, Int] = new HashMap()): Behavior[CommandStock] = {
    Behaviors.setup { context =>

      context.log.info("Stock")

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveItems]](WrappedSaveItem(_))

      if (mapStock.isEmpty) {
        context.system.receptionist ! Receptionist.Register(stockGuardianKey, context.self)

        val consumerController =
          context.spawn(ConsumerController(stockkey), "consumerControllerStock")
          consumerController ! ConsumerController.Start(deliveryAdapter)
      }

      Behaviors.receiveMessage {
        case WrappedSaveItem(delivery) =>

          // foldLeft goes sequentially over items and changes only the one with the current key or creates it
          val updatedMapStock: HashMap[Int, Int] = delivery.message.item.foldLeft(mapStock) { (map, item) =>
            val (itemId, quantity) = item
            map + (itemId -> (map.getOrElse(itemId, 0) + quantity))
          }

          delivery.confirmTo ! ConsumerController.Confirmed
          apply(updatedMapStock)

        case currentItem: PrintStock =>
          currentItem.replyTo ! PrintTotalStockOfItem(currentItem.id, mapStock.get(currentItem.id))
          Behaviors.same
      }
    }
  }

  sealed trait CommandStock
  case class SaveItems(item: Map[Int, Int]) extends CommandStock
  case class PrintStock(id: Int, replyTo: ActorRef[CommandReplyDumper]) extends CommandStock
  case class WrappedSaveItem(d: ConsumerController.Delivery[SaveItems]) extends CommandStock
}
