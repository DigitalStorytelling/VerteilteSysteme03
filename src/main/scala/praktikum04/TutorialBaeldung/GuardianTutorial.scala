package praktikum04.TutorialBaeldung

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import praktikum04.TutorialBaeldung.BankAccount.{Command, DepositCommand, DepositEvent, Response, WithdrawCommand}

object GuardianTutorial {
  def apply(): Behavior[BankAccount.Command] =
    Behaviors.setup { context =>
      /*val account1 = context.spawn(BankAccount(1), "BankAccount1")

      val messageAdapterDeposit = context.messageAdapter(StatusReply[BankAccount.Response](_))
      account1 ! DepositCommand(500, messageAdapterDeposit)

      val messageAdapterWithdraw = context.messageAdapter(StatusReply[BankAccount.Response](_))
      account1 ! WithdrawCommand(200, messageAdapterWithdraw)*/
      //todo:

      Behaviors.same
    }
}




