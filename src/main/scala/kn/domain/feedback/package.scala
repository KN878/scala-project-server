package kn.domain

import java.time.Instant

package object feedback {
  implicit class FeedbackSyntax(request: CreateFeedbackRequest) {
    def toFeedback(implicit customerId: Long): FeedbackWithIds =
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

  implicit class SecretCustomerSyntax(request: CreateFeedbackRequest) {
    def toSecretCustomer(implicit customerId: Long): FeedbackWithIds =
      FeedbackWithIds(
        None,
        request.shopId,
        customerId,
        request.pros,
        request.cons,
        request.additionalInfo,
        Instant.now(),
        FeedbackType.SecretCustomer,
      )
  }
}
