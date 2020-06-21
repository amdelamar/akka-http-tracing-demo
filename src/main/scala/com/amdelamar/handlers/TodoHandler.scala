package com.amdelamar.handlers

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.amdelamar.db.{Database, Todo}
import com.typesafe.scalalogging.Logger
import kamon.Kamon
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class TodoHandler(db: Database)
                 (implicit system: ActorSystem,
                  materializer: Materializer,
                  ex: ExecutionContext) {

  private val logger = Logger(this.getClass.getName)
  implicit val todoJsonformat = Todo.jsonFormat

  val routes: Route =
    pathPrefix("api" / "v1" / "todo") {
      pathEndOrSingleSlash {
        get {
          val response = fetchAllTodos()
          complete(response)
        } ~
        post {
          entity(as[String]) { body =>
            val response = saveTodo(body)
            complete(response)
          }
        } ~
        path(JavaUUID) { id =>
          get {
            val response = fetchTodo(id)
            complete(response)
          } ~
          delete {
            val response = deleteTodo(id)
            complete(response)
          }
        }
      }
    }

  def fetchAllTodos(): Future[HttpResponse] = {
    val requestId = Kamon.currentSpan().trace.id.string
    logger.debug(s"[$requestId] Fetching all todos from database.")

    val fetchSpan = Kamon.spanBuilder("get-all-todos").start()

    val response = Kamon.runWithSpan(fetchSpan)(db.getAllTodos()).map { todos =>
      fetchSpan.tag("todos.size", todos.size)

      if (todos.nonEmpty) {
        logger.debug(s"[$requestId] Found ${todos.size} todos to show.")
        HttpResponse(
          status = 200,
          entity = HttpEntity(ContentTypes.`application/json`,
          Json.prettyPrint(Json.toJson(todos))
        ))
      } else {
        logger.debug(s"[$requestId] Did not find any todos.")
        HttpResponse(
          status = 404,
          entity = HttpEntity(ContentTypes.`application/json`,
          Json.prettyPrint(Json.obj(
            "error" -> "No todos found."
          ))
        ))
      }
    }.recover {
      case e: Exception =>
        fetchSpan.tag("exception", e.getClass.toString)
        logger.error(s"[$requestId] Failed to fetch all todos.", Some(e))
        HttpResponse(
          status = 500,
          entity = HttpEntity(ContentTypes.`application/json`,
          Json.prettyPrint(Json.obj(
            "error" -> s"Failed to fetch all todos. Error: ${e.getMessage}"
          ))
        ))
    }
    fetchSpan.finish()
    response
  }

  def saveTodo(body: String): Future[HttpResponse] = {
    val requestId = Kamon.currentSpan().trace.id.string
    logger.debug(s"[$requestId] Saving todo in database.")

    val json = Json.parse(body)
    val titleOp = (json \ "title").asOpt[String]
    val importantOp = (json \ "important").asOpt[Boolean]

    val response = titleOp match {
      case Some(title) =>
        val newTodo = Todo(
          title = title,
          important = importantOp.getOrElse(false)
        )

        val insertSpan = Kamon.spanBuilder("insert-todo")
          .tag("id", newTodo.id.toString)
          .tag("title", newTodo.title)
          .start()

        val result = Kamon.runWithSpan(insertSpan)(db.insertTodo(newTodo)).map {
          case true =>
            logger.debug(s"[$requestId] Saved todo: ${newTodo.id}")
            HttpResponse(
              status = 201,
              entity = HttpEntity(ContentTypes.`application/json`,
              Json.prettyPrint(Json.toJson("Saved Todo"))
            ))
          case false =>
            logger.error(s"[$requestId] Failed to save todo: ${newTodo.id}")
            HttpResponse(
              status = 500,
              entity = HttpEntity(ContentTypes.`application/json`,
              Json.prettyPrint(Json.obj(
                "error" -> "Failed to save the todo, please try again shortly."
              ))
            ))
        }.recover {
          case e: Exception =>
            insertSpan.tag("exception", e.getClass.toString)
            logger.error(s"[$requestId] Failed to save the todo: ${newTodo.id}", Some(e))
            HttpResponse(
              status = 500,
              entity = HttpEntity(ContentTypes.`application/json`,
              Json.prettyPrint(Json.obj(
                "error" -> s"Failed to save the todo: ${newTodo.id} Error: ${e.getMessage}"
              ))
            ))
        }
        insertSpan.finish()
        result

      case _ =>
        logger.debug(s"[$requestId] Invalid todo.")
        Future.successful(
          HttpResponse(
            status = 400,
            entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.obj(
              "error" -> "Invalid todo. Expected 'title' and optional 'important' fields."
            ))
          ))
        )
    }
    response
  }

  def fetchTodo(id: UUID): Future[HttpResponse] = {
    val requestId = Kamon.currentSpan().trace.id.string
    logger.debug(s"[$requestId] Fetching todo in database.")

    val fetchSpan = Kamon.spanBuilder("get-todo").start()

    val response = Kamon.runWithSpan(fetchSpan)(db.getTodoById(id)).map {
      case Some(todo) =>
        fetchSpan.tag("todos.id", todo.id.toString)
        logger.debug(s"[$requestId] Found ${todo.id} todo to show.")
        HttpResponse(
          status = 200,
          entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.toJson(todo))
          ))

      case _ =>
        logger.debug(s"[$requestId] Did not find todo.")
        HttpResponse(
          status = 404,
          entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.obj(
              "error" -> "No todo found."
            ))
          ))
    }.recover {
      case e: Exception =>
        fetchSpan.tag("exception", e.getClass.toString)
        logger.error(s"[$requestId] Failed to fetch todo.", Some(e))
        HttpResponse(
          status = 500,
          entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.obj(
              "error" -> s"Failed to fetch todo. Error: ${e.getMessage}"
            ))
          ))
    }
    fetchSpan.finish()
    response
  }

  def deleteTodo(id: UUID): Future[HttpResponse] = {
    val requestId = Kamon.currentSpan().trace.id.string
    logger.debug(s"[$requestId] Deleting todo in database.")

    val deleteSpan = Kamon.spanBuilder("get-todo").start()

    val response = Kamon.runWithSpan(deleteSpan)(db.deleteTodoById(id)).map {
      case true =>
        deleteSpan.tag("todo.id", id.toString)
        logger.debug(s"[$requestId] Found ${id} todo to show.")
        HttpResponse(
          status = 200,
          entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.obj(
              "error" -> "Successfully deleted todo."
            ))
          ))

      case _ =>
        logger.debug(s"[$requestId] Did not find todo.")
        HttpResponse(
          status = 404,
          entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.obj(
              "error" -> "No todo found."
            ))
          ))
    }.recover {
      case e: Exception =>
        deleteSpan.tag("exception", e.getClass.toString)
        logger.error(s"[$requestId] Failed to delete todo.", Some(e))
        HttpResponse(
          status = 500,
          entity = HttpEntity(ContentTypes.`application/json`,
            Json.prettyPrint(Json.obj(
              "error" -> s"Failed to delete todo. Error: ${e.getMessage}"
            ))
          ))
    }
    deleteSpan.finish()
    response
  }

}
