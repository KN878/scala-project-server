package kn.arbitrary

import java.time.Instant

import kn.domain.feedback.FeedbackType.FeedbackType
import kn.domain.feedback.{FeedbackType, FeedbackWithIds}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

trait FeedbackArbitrary {
  implicit val feedbackType = Arbitrary[FeedbackType](Gen.oneOf[FeedbackType](FeedbackType.values.toIndexedSeq))

  def feedbackWithIds(shopId: Long, customerId: Long): Arbitrary[FeedbackWithIds] = Arbitrary[FeedbackWithIds] {
    for {
      id <- Gen.option(Gen.posNum[Long])
      pros <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      cons <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      additionalInfo <- Gen.option(Gen.listOfN(8, Gen.alphaChar).map(_.mkString))
      date <- arbitrary[Instant]
      type_ <- arbitrary[FeedbackType]
    } yield FeedbackWithIds(id, shopId, customerId, pros, cons, additionalInfo, date, type_)
  }
}

object FeedbackArbitrary extends FeedbackArbitrary