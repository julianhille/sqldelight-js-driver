package com.github.julianhille.sqldelight.drivers.jscipher

import com.squareup.sqldelight.db.SqlCursor
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.db.SqlPreparedStatement
import com.squareup.sqldelight.Transacter
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Int8Array

@JsNonModule @JsModule("better-sqlite3-multiple-ciphers")
external fun createDatabase(path: String? = definedExternally, options: DatabaseOptions? = definedExternally): Database

class DatabaseConfiguration(
  val name: String,
  var path: String,
  var schema: SqlDriver.Schema,
  var create: (SqlDriver) -> Unit,
  var upgrade: (SqlDriver, Int, Int) -> Unit,
  var journalMode: Boolean = true,
  val memory: Boolean = false,
  var key: String? = null,
) {
  fun dbPath(): String {
    if (memory) return ":memory:"
    return path + name
  }
}

class SqlJsCipherDriver (var configuration: DatabaseConfiguration): SqlDriver {
  private val db = createDatabase(configuration.dbPath(), js("{ verbose: console.log }"))
  private val statements = mutableMapOf<Int, SqlJsCipherStatement>()
  private var transaction: Transacter.Transaction? = null

  init {
    if (configuration.journalMode ) {
      db.pragma("journal_mode = WAL")
    }
    if (configuration.key?.isNotBlank() == true) {
      db.pragma("key = WAL")
    }
    migrateIfNeeded(configuration.create, configuration.upgrade, configuration.schema.version)
  }

  override fun close() {
    db.close()
  }

  override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?) {
    createOrGetStatement(identifier, sql).run {
      bind(binders)
      run()
    }
  }

  private fun Statement.bind(binders: (SqlPreparedStatement.() -> Unit)?) = binders?.let {
    val bound = SqlJsCipherStatement()
    binders(bound)
    println(bound.parameters)
    bind(bound.parameters.toTypedArray())
  }

  override fun executeQuery(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?
  ): SqlCursor {
    return createOrGetStatement(identifier, sql).run {
      bind(binders)
      SqlJsCipherCursor(iterate())
    }
  }

  override fun newTransaction(): Transacter.Transaction {
    val enclosing = transaction
    val transaction = Transaction(enclosing)
    this.transaction = transaction
    if (enclosing == null) {
      db.exec("BEGIN TRANSACTION")
    }
    return transaction
  }

  override fun currentTransaction() = transaction

  private inner class Transaction(
    override val enclosingTransaction: Transacter.Transaction?
  ) : Transacter.Transaction() {
    override fun endTransaction(successful: Boolean) {
      if (enclosingTransaction == null) {
        if (successful) {
          db.exec("END TRANSACTION")
        } else {
          db.exec("ROLLBACK TRANSACTION")
        }
      }
      transaction = enclosingTransaction
    }
  }

  private fun createOrGetStatement(identifier: Int?, sql: String): Statement = db.prepare(sql)

  fun getVersion(): Int = (db.pragma("user_version") as? Double)?.toInt() ?: 0

  private fun setVersion(version: Int): Unit {
    db.pragma("user_version=${version}")
  }

  fun migrateIfNeeded(
    create: (SqlDriver) -> Unit,
    upgrade: (SqlDriver, Int, Int) -> Unit,
    version: Int
  ) {
    val initialVersion = getVersion()
    println("soimething mumble mumble $initialVersion")
    println("soimething mumble mumble $db")
    println("soimething mumble mumble $transaction")
    val driver = this
    if (initialVersion == 0) {
      transaction.run {
        create(driver)
        setVersion(version)
      }
    } else if (initialVersion != version) {
      if (initialVersion > version) {
          throw IllegalStateException("Database version $initialVersion newer than config version $version")
        }
        transaction.run {
          upgrade(driver, initialVersion, version)
          setVersion(version)
        }
    }
  }
}

class SqlJsCipherStatement: SqlPreparedStatement {
  val parameters = mutableListOf<Any?>()
  override fun bindBytes(index: Int, bytes: ByteArray?) {
    parameters.add(bytes)
  }

  override fun bindLong(index: Int, long: Long?) {
    // We convert Long to Double because Kotlin's Double is mapped to JS number
    // whereas Kotlin's Long is implemented as a JS object
    parameters.add(long?.toDouble())
  }

  override fun bindDouble(index: Int, double: Double?) {
    parameters.add(double)
  }

  override fun bindString(index: Int, string: String?) {
    parameters.add(string)
  }

  fun bindBoolean(index: Int, boolean: Boolean?) {
    parameters.add(
      when (boolean) {
        null -> null
        true -> 1.0
        false -> 0.0
      }
    )
  }
}

class SqlJsCipherCursor(private val statementIterator: StatementIterator): SqlCursor {
  var lastResult: IteratorResult? = null
  var columns: List<String> = listOf<String>()

  private fun getIndex(index: Int):  dynamic? /* Number | String | Uint8Array | Nothing? */ {
    val name = columns[index]
    val value = lastResult?.value ?: throw NoSuchElementException()
    return js("value[name]")
  }

  override fun close() {
    statementIterator.`return`()
  }

  override fun getString(index: Int): String? = getIndex(index)
  override fun getLong(index: Int): Long? = (getIndex(index) as? Double)?.toLong()
  override fun getBytes(index: Int): ByteArray? = (getIndex(index) as? Uint8Array)?.let {
    Int8Array(it.buffer).unsafeCast<ByteArray>()
  }

  override fun getDouble(index: Int): Double? = getIndex(index)

  fun getBoolean(index: Int): Boolean? {
    val double = (getIndex(index) as? Double)
    return double?.let { double.toLong() == 1L }
  }

  override fun next(): Boolean {
    if (columns.isEmpty()) {
      columns = statementIterator.statement.columns().map {
        it.name
      }
    }
    lastResult = statementIterator.next()
    console.log(lastResult)

    return lastResult!!.done.not()
  }
}

