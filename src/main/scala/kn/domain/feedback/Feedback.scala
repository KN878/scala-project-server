package kn.domain.feedback

import java.time.Instant

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class Feedback(
    id: Option[Long],
    shopId: Long,
    customerId: Long,
    pros: String,
    cons: String,
    additionalInfo: Option[String],
    date: Instant,
)

case class CreateFeedbackRequest(
    shopId: Long,
    pros: String,
    cons: String,
    additionalInfo: Option[String],
)
