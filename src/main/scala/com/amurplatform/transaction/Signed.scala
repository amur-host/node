package com.amurplatform.transaction

import monix.eval.{Coeval, Task}
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import com.amurplatform.transaction.ValidationError.InvalidSignature

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait Signed extends Authorized {
  protected val signatureValid: Coeval[Boolean]

  protected val signedDescendants: Coeval[Seq[Signed]] = Coeval.evalOnce(Seq.empty)

  protected val signaturesValidMemoized: Task[Either[InvalidSignature, this.type]] = Signed.validateTask[this.type](this).memoize

  val signaturesValid: Coeval[Either[InvalidSignature, this.type]] =
    Coeval.evalOnce(Await.result(signaturesValidMemoized.runAsync(Signed.scheduler), Duration.Inf))
}

object Signed {

  type E[A] = Either[InvalidSignature, A]
  private implicit val scheduler: SchedulerService = Scheduler.computation(name = "sig-validator")

  private def validateTask[S <: Signed](s: S): Task[E[S]] =
    Task {
      if (!s.signatureValid()) Task.now(Left(InvalidSignature(s, None)))
      else if (s.signedDescendants().isEmpty) Task.now(Right(s))
      else
        Task.wanderUnordered(s.signedDescendants())(s => s.signaturesValidMemoized) map { l =>
          l.find(_.isLeft) match {
            case Some(e) => Left(e.left.get)
            case None    => Right(s)
          }
        }
    }.flatten

  def validateOrdered[S <: Signed](ss: Seq[S]): E[Seq[S]] =
    Await.result(Task
                   .wander(ss)(s => s.signaturesValidMemoized)
                   .map { l =>
                     l.find(_.isLeft) match {
                       case Some(e) => Left(e.left.get)
                       case None    => Right(ss)
                     }
                   }
                   .runAsync,
                 Duration.Inf)

}
