package kn.domain.feedback

import java.time.Instant

case class FeedbackWithIds(
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

case class Feedback(
    id: Option[Long],
    shopName: String,
    customerEmail: String,
    pros: String,
    cons: String,
    additionalInfo: Option[String],
    date: Instant,
)
