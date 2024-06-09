package praktikum04.TutorialBaeldung

import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import praktikum04.TutorialBaeldung.BankAccount.Command

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {

    val systemActor = ActorSystem[Command](GuardianTutorial(), "order-system")

    System.out.println("~~ Press any key to close ~~")
    StdIn.readLine
    systemActor.terminate()
  }
}
