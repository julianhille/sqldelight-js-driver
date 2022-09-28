@file:JsModule("better-sqlite3-multiple-ciphers")
@file:JsNonModule
@file:Suppress("unused")

package com.github.julianhille.sqldelight.drivers.jscipher
import kotlin.js.Promise

open external class Error

open external class SqliteError: Error {
  var name: String
  var code: String
  var message: String
}

external class DatabaseOptions {
  var readonly: Boolean = definedExternally // = false
  val fileMustExist: Boolean = definedExternally // = false
  val timeout: Int = definedExternally // = 5000
  val verbose: Boolean = definedExternally // = false
  val nativeBindingPath: Boolean = definedExternally // = false
}

external object PragmaOptions {
  var simple: Boolean? = definedExternally
}

external interface PragmaOption {
  var simple: Boolean?
}

external interface BackupOptions
external interface Buffer
external interface SerializeOptions
external interface UserDefinedFunctionOption
external interface AggregateOptions
external interface VirtualTableOptions
external interface EntryPointOptions
external interface Row

external class ColumnResult {
  var name: String
  var column: String
  var table: String
  var database: String
  var type: String
}

external class Result {
  val changes: Int? = definedExternally
  val lastInsertRowid: Int? = definedExternally
}

external class IteratorResult {
  var value: Map<String, Any>? = definedExternally
  var done: Boolean
}

external class StatementIterator {
  fun next(): IteratorResult
  var statement: Statement
  fun `return`(): IteratorResult
}

open external class Statement {
  var busy: Boolean
  val reader: Boolean
  val readonly: Boolean
  val source: String

  val database: Database

  fun pluck(state: Boolean? = definedExternally): Statement
  fun expand(state: Boolean? = definedExternally): Statement
  fun raw(state: Boolean? = definedExternally): Statement
  fun get(vararg parameters: Any): Row?
  fun all(vararg parameters: Any): Array<Row>
  fun run(vararg parameters: Any): Result
  fun iterate(vararg parameters: Any): StatementIterator
  fun bind(vararg parameters: Any): Statement
  fun columns(): Array<ColumnResult>
}

open external class Database() {
  var open: Boolean
  var inTransaction: Boolean
  var name: String
  var memory: Boolean
  var readonly: Boolean

  constructor(memory: Array<Byte>, dbOptions: DatabaseOptions? = definedExternally)
  constructor(path: String, dbOptions: DatabaseOptions? = definedExternally)

  fun prepare(sql: String): Statement
  fun exec(sql: String): Database
  fun close (): Database
  fun transaction (func: () -> Unit): () -> Unit
  fun pragma (sql: String, option: PragmaOptions? = definedExternally): Array<dynamic /* Number | String | Boolean | Nothing? */>

  fun backup(destination: String, options: BackupOptions?): Promise<Unit>
  fun serialize (options: SerializeOptions? = definedExternally): Buffer
  fun function(name: String, options: UserDefinedFunctionOption, function: () -> dynamic): Database
  fun function(name: String, function: () -> dynamic): Database
  fun aggregate(name: String, options: AggregateOptions): Database
  fun table(name: String, definition: VirtualTableOptions): Database
  fun loadExtension(path: String, entryPoint: EntryPointOptions? = definedExternally): Database
}

