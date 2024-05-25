package praktikum3

import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import praktikum3.ReplyDumper.{CommandReplyDumper, PrintTotalStockOfItem}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable.HashMap

object Stock  {

  val stockkey = ServiceKey[ConsumerController.Command[SaveItems]]("Stock")

  val StockGuardianKey: ServiceKey[CommandStock] = ServiceKey[CommandStock]("StockGuardian")


  def apply(mapStock: HashMap[Int, Int] = new HashMap()): Behavior[CommandStock] = {
    Behaviors.setup { (context) =>

      context.log.info("Stock")
      context.system.receptionist ! Receptionist.Register(StockGuardianKey, context.self)

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveItems]](WrappedDelivery(_))

      if (mapStock.isEmpty) {
        val consumerController =
          context.spawn(ConsumerController(stockkey), "consumerControllerStock")
          consumerController ! ConsumerController.Start(deliveryAdapter)
      }

      Behaviors.receiveMessage {
        case WrappedDelivery(delivery) =>

          // todo: is right but check logic
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
  case class WrappedDelivery(d: ConsumerController.Delivery[SaveItems]) extends CommandStock
}
