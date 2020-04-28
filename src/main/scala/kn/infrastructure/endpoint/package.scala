package kn.infrastructure

import java.time.{Instant, ZoneId}

import io.circe.Encoder
import kn.domain.users.User
import org.http4s.Response
import tsec.authentication.{AugmentedJWT, SecuredRequest, TSecAuthService}

package object infrastructure {
  type AuthService[Auth, F[_]] = TSecAuthService[User, AugmentedJWT[Auth, Long], F]
  type AuthEndpoint[Auth, F[_]] =
    PartialFunction[SecuredRequest[F, User, AugmentedJWT[Auth, Long]], F[Response[F]]]

  implicit val instantEncoder: Encoder[Instant] =
    Encoder.encodeString.contramap[Instant](_.atZone(ZoneId.of("Europe/Moscow")).toString)

}
