package com.amdelamar.db

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.Json

case class Todo(title: String,
                id: UUID = UUID.randomUUID,
                datetime: LocalDateTime = LocalDateTime.now(),
                important: Boolean = false)

object Todo {
  val jsonFormat = Json.format[Todo]
}
