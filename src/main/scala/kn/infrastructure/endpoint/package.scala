package kn.infrastructure

import kn.domain.users.User
import org.http4s.Response
import tsec.authentication.{AugmentedJWT, SecuredRequest, TSecAuthService}

package object infrastructure {
  type AuthService[Auth, F[_]] = TSecAuthService[User, AugmentedJWT[Auth, Long], F]
  type AuthEndpoint[Auth, F[_]] =
    PartialFunction[SecuredRequest[F, User, AugmentedJWT[Auth, Long]], F[Response[F]]]
}
