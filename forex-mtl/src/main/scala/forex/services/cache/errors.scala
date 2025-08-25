package forex.services.cache

object errors {

  sealed trait Error
  object Error {
    final case class CacheLookupFailed(msg: String) extends Error
  }

}
