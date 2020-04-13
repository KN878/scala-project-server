package kn.config

final case class ServerConfig(host: String, port: Int)
final case class MobileServerConfig(db: DatabaseConfig, server: ServerConfig)
