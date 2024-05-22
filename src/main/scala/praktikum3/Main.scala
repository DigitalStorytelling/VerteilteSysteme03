package praktikum3

import akka.actor.typed.ActorSystem

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {
    val systemActor = ActorSystem[Nothing](Guardian(), "order-system")

    System.out.println("~~ Press any key to close ~~")
    StdIn.readLine
    systemActor.terminate()
  }
}
