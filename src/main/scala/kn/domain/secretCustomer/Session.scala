package kn.domain.secretCustomer

import java.time.Instant

import io.circe.{Encoder, Json, JsonNumber, JsonObject}
import kn.domain.secretCustomer.SessionStage.SessionStage

object SessionStage extends Enumeration{
  type SessionStage = Value
  val CompletingActions = Value(1)
  val LeavingFeedback = Value(2)
  val Completed = Value(3)
}

case class Session(id: Option[Long], shopId: Long, customerId: Long, stage: SessionStage, started: Instant, expiresAt: Instant)
case class CreateSession(shopId: Long)