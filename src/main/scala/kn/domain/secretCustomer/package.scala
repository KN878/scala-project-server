package kn.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

package object secretCustomer {
  implicit def createSessionConversion(
      createSession: CreateSession,
  )(implicit customerId: Long): Session =
    Session(
      None,
      createSession.shopId,
      customerId,
      SessionStage.CompletingActions,
      Instant.now(),
      Instant.now().plus(30, ChronoUnit.MINUTES),
    )
}
