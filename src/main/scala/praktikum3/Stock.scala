package praktikum3

import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.receptionist.ServiceKey
import praktikum3.ReplyDumper.{CommandReplyDumper, PrintTotalStockOfItem}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable.HashMap

object Stock {

  val stockkey = ServiceKey[ConsumerController.Command[SaveItems]]("Stock")

  def apply(mapStock: HashMap[Int, Int] = new HashMap()): Behavior[CommandStock] = {
    Behaviors.setup { (context) =>

      context.log.info("Stock")

      val deliveryAdapter =
        context.messageAdapter[ConsumerController.Delivery[SaveItems]](WrappedDelivery(_))

      if (mapStock.isEmpty) {
        val consumerController =
          context.spawn(ConsumerController(stockkey), "consumerController")
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

        case unknownMessage =>
          context.log.warn(s"Received unknown message in STOCK: ${unknownMessage.getClass.getSimpleName}")
          Behaviors.same
      }
    }
  }

  sealed trait CommandStock
  case class SaveItems(item: Map[Int, Int]) extends CommandStock
  case class PrintStock(id: Int, replyTo: ActorRef[CommandReplyDumper]) extends CommandStock

  case class WrappedDelivery(d: ConsumerController.Delivery[SaveItems]) extends CommandStock
}
