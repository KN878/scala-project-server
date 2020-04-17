package kn.domain

import java.time.Instant

package object feedback {
  implicit def createFeedbackRequestToFeedback(request: CreateFeedbackRequest)(implicit customerId: Long): Feedback =
    Feedback(None, request.shopId, customerId, request.pros, request.cons, request.additionalInfo, Instant.now())
}
