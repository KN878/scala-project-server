package kn.utils.validation

import cats.Monad
import cats.data.EitherT

trait ValidationResultLib[M[_]] {

  type ValidationResult[F, S] = EitherT[M, F, S]

  object ValidationResult {

    def successful[F, S](s: S)(implicit m: Monad[M]): ValidationResult[F, S] =
      EitherT.rightT(s)

    def failed[F, S](f: F)(implicit m: Monad[M]): ValidationResult[F, S] =
      EitherT.leftT(f)

    def ensure[F](c: => Boolean, onFailure: => F)(implicit m: Monad[M]): ValidationResult[F, Unit] =
      EitherT.cond[M](c, (), onFailure)

    def ensureM[F](c: => M[Boolean], onFailure: => F)(implicit m: Monad[M]): ValidationResult[F, Unit] =
      EitherT.right(c).ensure(onFailure)(b => b).map(_ => ())

    def fromOptionM[F, S](opt: M[Option[S]], ifNone: => F)(implicit m: Monad[M]): ValidationResult[F, S] =
      EitherT.fromOptionF(opt, ifNone)
  }
}
