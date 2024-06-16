package praktikum4

import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {
    //port and role as env
    val systemActor = ActorSystem[Receptionist.Listing](Guardian(), "order-system")

    System.out.println("~~ Press any key to close ~~")
    StdIn.readLine
    systemActor.terminate()
  }
}
