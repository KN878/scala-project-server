package kn.domain.feedback

object FeedbackType extends Enumeration {
  type FeedbackType = Value
  val Feedback = Value("feedback")
  val SecretCustomer = Value("secret_customer")
}
