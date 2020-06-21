package com.amdelamar

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.stream.ActorMaterializer
import com.amdelamar.db.{Database, Todo}
import com.amdelamar.handlers.TodoHandler
import kamon.Kamon
import com.typesafe.scalalogging.Logger

import scala.util.Try

object App {

  private val logger = Logger("App")

  val HOST = Try(sys.env("HOST")).getOrElse("localhost")
  val PORT = Try(sys.env("PORT").toInt).getOrElse(8080)
  val APP = sys.env.getOrElse("APP_NAME", "my-app")

  implicit val system = ActorSystem(APP)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatchers.lookup("blocking-dispatcher")

  def main(args: Array[String]): Unit = {
    Kamon.init()

    val db = new Database()
    db.insertTodo(Todo("Hello World"))
    val todoHandler = new TodoHandler(db)

    Http().bindAndHandle(
      extractRequestContext { ctx =>
        // log all incoming http requests
        logHttpRequest(ctx)

        pathEndOrSingleSlash {
          complete(s"$APP is healthy.\n")
        } ~ todoHandler.routes
      },
      HOST, PORT)
      .map { _ =>
        logger.info(s"$APP running at http://$HOST:$PORT/")
      } recover {
      case ex =>
        logger.error(s"Failed to bind to $HOST:$PORT.", ex.getMessage)
    }
  }

  /**
   * Logs the incoming HTTP Request.
   */
  def logHttpRequest(ctx: RequestContext): Unit = {
    val protocol = ctx.request.protocol.value
    val method = ctx.request.method.value
    val uri = ctx.request.uri.path.toString
    val requestId = Kamon.currentSpan().trace.id.string
    logger.info(s"[$requestId] $protocol $method $uri")
  }
}
