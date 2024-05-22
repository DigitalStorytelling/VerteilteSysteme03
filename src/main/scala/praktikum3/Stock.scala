package praktikum3

import praktikum3.ReplyDumper.{CommandReplyDumper, PrintTotalStockOfItem}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable.HashMap

object Stock {

  def apply(mapStock: HashMap[Int, Int] = new HashMap()): Behavior[CommandStock] = {
    Behaviors.setup { (context) =>

      Behaviors.receiveMessage {
        case currentItems: SaveItems =>

          // todo: is right but check logic
          val updatedMapStock: HashMap[Int, Int] = currentItems.item.foldLeft(mapStock) { (map, item) =>
            val (itemId, quantity) = item
            map + (itemId -> (map.getOrElse(itemId, 0) + quantity))
          }

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

}
