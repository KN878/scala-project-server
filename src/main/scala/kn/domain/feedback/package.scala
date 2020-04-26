package kn.domain

import java.time.Instant

package object feedback {
  implicit def createFeedbackRequestToFeedback(
      request: CreateFeedbackRequest,
  )(implicit customerId: Long): FeedbackWithIds =
    FeedbackWithIds(
      None,
      request.shopId,
      customerId,
      request.pros,
      request.cons,
      request.additionalInfo,
      Instant.now(),
      FeedbackType.Feedback,
    )
}
