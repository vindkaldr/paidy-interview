package forex

import cats.effect._
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import forex.config._
import fs2.Stream
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- Config.stream[IO]("app").compile.lastOrError
      exitCode <- new Application[IO].stream(executionContext, config).compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}

class Application[F[_]: ConcurrentEffect: Timer](implicit cs: ContextShift[F]) {
  implicit val log: Log[F] = Log.NoOp.instance

  private def httpClient(context: ExecutionContext): Resource[F, Client[F]] =
    BlazeClientBuilder[F](context).resource

  private def redisClient(config: ApplicationConfig): Resource[F, RedisCommands[F, String, String]] =
    Redis[F].utf8(s"redis://${config.redis.host}:${config.redis.port}")

  private def inBackground(job: F[Unit]): Stream[F, Unit] = {
    Stream.eval(job) ++
      Stream.awakeEvery[F](5.seconds).evalMap { _ => job }
  }

  def stream(context: ExecutionContext, config: ApplicationConfig): Stream[F, ExitCode] =
    for {
      httpClient <- Stream.resource(httpClient(context))
      redis  <- Stream.resource(redisClient(config))
      module = new Module[F](config, httpClient, redis)
      exitCode <- BlazeServerBuilder[F](context)
        .bindHttp(config.http.port, config.http.host)
        .withHttpApp(module.httpApp)
        .serve
        .concurrently(inBackground(module.ratesProgram.buildCache()))
    } yield exitCode
}
