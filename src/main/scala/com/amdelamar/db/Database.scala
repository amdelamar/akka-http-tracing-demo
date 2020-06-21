package com.amdelamar.db

import java.util.UUID

import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class Database(implicit val ex: ExecutionContext) {

  private val logger = Logger("Database")

  val cache: Cache[UUID, Todo] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(24.hours)
      .maximumSize(100)
      .build[UUID, Todo]()

  def getAllTodos(): Future[Seq[Todo]] = Future {
    logger.debug(s"Fetching All Todos")
    cache.asMap().values.toList
  }

  def insertTodo(todo: Todo): Future[Boolean] = Future {
    logger.debug(s"Inserting Todo: ${todo.id}")
    cache.put(todo.id, todo)
    true
  }

  def hasTodoById(id: UUID): Future[Boolean] = Future {
    logger.debug(s"Checking Todo exists: ${id}")
    cache.getIfPresent(id).isDefined
  }

  def getTodoById(id: UUID): Future[Option[Todo]] = Future {
    logger.debug(s"Fetching Todo: ${id}")
    cache.getIfPresent(id)
  }

  def deleteTodoById(id: UUID): Future[Boolean] = Future {
    logger.debug(s"Deleting Todo: ${id}")
    cache.getIfPresent(id).map { _ =>
      cache.invalidate(id)
      true
    }.getOrElse(false)
  }

}
