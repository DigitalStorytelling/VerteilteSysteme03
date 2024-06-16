package praktikum4

import praktikum4.ShipPriority.{HIGH,LOW,URGENT, ShipPriority,MEDIUM, NOT_SPECIFIED}
import io.circe._
import io.circe.generic.semiauto._

import java.time.LocalDate

case class Customer(id: Int,
                    name: String,
                    address: String,
                    nation: String,
                    region: String,
                    phone: String,
                    marketSegment: String)
object Customer {
  implicit val decoder: Decoder[Customer] = deriveDecoder[Customer]
}

case class Order(id: Int,
                 customer: Customer,
                 date: LocalDate,
                 shipPriority: ShipPriority,
                 items: List[Item])
object Order {

  implicit val shipPriorityDecoder: Decoder[ShipPriority] = Decoder.decodeString.emap { str =>
    val parts = str.split("-")

      parts(1) match {
        case "URGENT" => Right(URGENT)
        case "HIGH" => Right(HIGH)
        case "MEDIUM" => Right(MEDIUM)
        case "NOT SPECIFIED" => Right(NOT_SPECIFIED)
        case "LOW" => Right(LOW)
      }
  }

  implicit val decoder: Decoder[Order] = deriveDecoder[Order]
}

case class Item(id: Int,
                name: String,
                mfgr: String,
                color: String,
                style: List[String],
                size: Int,
                quantity: Int,
                price: Int)
  object Item {
    implicit val itemDecoder: Decoder[List[String]] = Decoder.instance(c =>
      c.as[String].map(_.split(" ").toList)
    )
    implicit val decoder: Decoder[Item] = deriveDecoder[Item]
  }