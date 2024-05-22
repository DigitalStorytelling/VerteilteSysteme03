package praktikum3

import praktikum3.OrderDispatcher.{CommandOrderDispatcher, NextOrder, Empty}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import io.circe.jawn.decode

import scala.io.Source

object Reader {

  def apply(): Behavior[CommandReader] = Behaviors.receive {
    val file = "orders.json/orders.json"
    val document = Source.fromFile(file)
    val lines = document.getLines()

    (context, message) =>
      message match {
        case next: Next if lines.hasNext =>
          val line = lines.next()

          //Sicherstellen das nicht Start oder Ende der File ist, da diese
          // keine richtigen JSON sind und daher nicht ausgelesen werden sollen
          if (!line.startsWith("[") && !line.startsWith("]")) {
            val parseResult = decode[Order](line.dropRight(1))

            parseResult match {
              case Right(order) =>
                next.replyTo ! NextOrder(order)
              case Left(error) =>
                println(s"Failed to decode line: $line. Error: $error")
                document.close()
            }
          }
          else {
            context.self ! Next(next.replyTo)
          }

        // Keine next Line
        case next: Next =>
          document.close()
          next.replyTo ! Empty
      }
      Behaviors.same
  }

  sealed trait CommandReader
  case class Next(replyTo: ActorRef[CommandOrderDispatcher]) extends CommandReader

}