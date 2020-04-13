import io.circe.Decoder
import io.circe.generic.semiauto._
import kn.config.{DatabaseConfig, MobileServerConfig, ServerConfig}

package object kn {
  implicit val srDec: Decoder[ServerConfig] = deriveDecoder
  implicit val dbDec: Decoder[DatabaseConfig] = deriveDecoder
  implicit val msDec: Decoder[MobileServerConfig] = deriveDecoder
}
