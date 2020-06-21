package com.amdelamar

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import com.amdelamar.db.{Database, Todo}
import com.amdelamar.handlers.TodoHandler
import kamon.Kamon
import com.typesafe.scalalogging.Logger

import scala.util.Try

object App {

  private val logger = Logger("App")
  val HOST = Try(sys.env("HOST")).getOrElse("localhost")
  val PORT = Try(sys.env("PORT").toInt).getOrElse(8080)

  implicit val system = ActorSystem("my-app")
  implicit val executionContext = system.dispatchers.lookup("custom-dispatcher")

  /**
   * Main Method
   * @param args
   */
  def main(args: Array[String]): Unit = {
    Kamon.init() // This must be first.

    // Setup our demo app.
    val db = new Database()
    db.insertTodo(Todo("Hello World!"))
    val todoHandler = new TodoHandler(db)

    // This Akka Http setup is not really important. Check TodoHandler for Kamon spans.
    Http().bindAndHandle(
      extractRequestContext { ctx =>
        // log all incoming http requests
        logHttpRequest(ctx)

        pathEndOrSingleSlash {
          complete("App is healthy.\n")
        } ~ todoHandler.routes
      },
      HOST, PORT)
      .map { _ =>
        logger.info(s"App is running at http://$HOST:$PORT/")
      } recover {
      case ex =>
        logger.error(s"Failed to bind to $HOST:$PORT.", ex.getMessage)
    }
  }

  /**
   * Logs the incoming HTTP Request.
   *
   * We log this for demo purposes, to make it easier to see traces and
   * compare them with the log output.
   */
  def logHttpRequest(ctx: RequestContext): Unit = {
    val protocol = ctx.request.protocol.value
    val method = ctx.request.method.value
    val uri = ctx.request.uri.path.toString

    // Kamon is configured to use the X-Request-ID header as the trace id.
    // We grab it here so we can log it.
    val requestId = Kamon.currentSpan().trace.id.string
    logger.info(s"[$requestId] $protocol $method $uri")
  }
}
