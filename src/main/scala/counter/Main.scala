package counter

import akka.actor.typed.ActorSystem
import counter.Counter.CounterCommand
import counter.GuardianCounter.GuardianCommand

import scala.io.StdIn
object Main {

  def main(args: Array[String]): Unit = {
    val systemActor = ActorSystem[GuardianCommand](GuardianCounter(), "order-system")

    System.out.println("~~ Press any key to close ~~")
    StdIn.readLine

    systemActor.terminate()
  }
}
