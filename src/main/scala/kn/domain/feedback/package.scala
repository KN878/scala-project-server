package kn.domain

import java.time.Instant

package object feedback {
  implicit def createFeedbackRequestToFeedback(request: CreateFeedbackRequest): Feedback =
    Feedback(None, request.shopId, request.customerId, request.pros, request.cons, request.additionalInfo, Instant.now())
}
