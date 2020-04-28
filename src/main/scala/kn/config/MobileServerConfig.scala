package kn.config

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ServerConfig(host: String, port: Int)
object ServerConfig {
  implicit val srDec: Decoder[ServerConfig] = deriveDecoder
}

final case class SecretCustomerConfig(charge: Float)
object SecretCustomerConfig {
  implicit val scDec: Decoder[SecretCustomerConfig] = deriveDecoder
}

final case class MobileServerConfig(db: DatabaseConfig, server: ServerConfig, secretCustomer: SecretCustomerConfig)
object MobileServerConfig {
  implicit val msDec: Decoder[MobileServerConfig] = deriveDecoder
}
