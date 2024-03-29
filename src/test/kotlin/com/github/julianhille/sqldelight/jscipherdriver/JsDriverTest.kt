package com.github.julianhille.sqldelight.drivers.jscipher

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.db.SqlPreparedStatement
import com.squareup.sqldelight.db.use

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

open class JsDriverTest {

  internal lateinit var driver: SqlJsCipherDriver

  internal val schema = object : SqlDriver.Schema {
    override val version: Int = 1

    override fun create(driver: SqlDriver) {
      driver.execute(
        0,
        """
              |CREATE TABLE test (
              |  id INTEGER PRIMARY KEY,
              |  value TEXT
              |);
            """.trimMargin(),
        0
      )
      driver.execute(
        1,
        """
              |CREATE TABLE nullability_test (
              |  id INTEGER PRIMARY KEY,
              |  integer_value INTEGER,
              |  text_value TEXT,
              |  blob_value BLOB,
              |  real_value REAL
              |);
            """.trimMargin(),
        0
      )
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Int,
      newVersion: Int
    ) {
      // No-op.
    }
  }

  @BeforeTest
  open fun setup() {
    val config = MemoryDatabaseConfiguration(schema, {
      schema.create(it)
    }, { driver, oldVersion, newVersion ->
      schema.migrate(driver, oldVersion, newVersion)
    })
    driver = SqlJsCipherDriver(config)
  }

  @AfterTest
  fun tearDown() {
    driver.close()
  }

  @Test fun insert_can_run_multiple_times() {
    val insert = { binders: SqlPreparedStatement.() -> Unit ->
      driver.execute(2, "INSERT INTO test VALUES (?, ?);", 2, binders)
    }
    val query = {
      driver.executeQuery(3, "SELECT * FROM test", 0)
    }
    val changes = {
      driver.executeQuery(4, "SELECT changes()", 0)
    }

    query().use {
      assertFalse(it.next())
    }

    insert {
      bindLong(1, 1)
      bindString(2, "Alec")
    }

    query().use {
      assertTrue(it.next())
      assertFalse(it.next())
    }

    assertEquals(1, changes().apply { next() }.use { it.getLong(0) })

    query().use {
      assertTrue(it.next())
      assertEquals(1, it.getLong(0))
      assertEquals("Alec", it.getString(1))
    }

    insert {
      bindLong(1, 2)
      bindString(2, "Jake")
    }
    assertEquals(1, changes().apply { next() }.use { it.getLong(0) })

    query().use {
      assertTrue(it.next())
      assertEquals(1, it.getLong(0))
      assertEquals("Alec", it.getString(1))
      assertTrue(it.next())
      assertEquals(2, it.getLong(0))
      assertEquals("Jake", it.getString(1))
    }

    driver.execute(5, "DELETE FROM test", 0)
    assertEquals(2, changes().apply { next() }.use { it.getLong(0) })

    query().use {
      assertFalse(it.next())
    }
  }

  @Test fun query_can_run_multiple_times() {

    val insert = { binders: SqlPreparedStatement.() -> Unit ->
      driver.execute(2, "INSERT INTO test VALUES (?, ?);", 2, binders)
    }
    val changes = {
      driver.executeQuery(4, "SELECT changes()", 0)
    }

    insert {
      bindLong(1, 1)
      bindString(2, "Alec")
    }
    assertEquals(1, changes().apply { next() }.use { it.getLong(0) })
    insert {
      bindLong(1, 2)
      bindString(2, "Jake")
    }
    assertEquals(1, changes().apply { next() }.use { it.getLong(0) })

    val query = { binders: SqlPreparedStatement.() -> Unit ->
      driver.executeQuery(6, "SELECT * FROM test WHERE value = ?", 1, binders)
    }
    query {
      bindString(1, "Jake")
    }.use {
      assertTrue(it.next())
      assertEquals(2, it.getLong(0))
      assertEquals("Jake", it.getString(1))
    }

    // Second time running the query is fine
    query {
      bindString(1, "Jake")
    }.use {
      assertTrue(it.next())
      assertEquals(2, it.getLong(0))
      assertEquals("Jake", it.getString(1))
    }
  }

  @Test fun sqlResultSet_getters_return_null_if_the_column_values_are_NULL() {

    val insert = { binders: SqlPreparedStatement.() -> Unit ->
      driver.execute(7, "INSERT INTO nullability_test VALUES (?, ?, ?, ?, ?);", 5, binders)
    }
    val changes = { driver.executeQuery(4, "SELECT changes()", 0) }
    insert {
      bindLong(1, 1)
      bindLong(2, null)
      bindString(3, null)
      bindBytes(4, null)
      bindDouble(5, null)
    }
    assertEquals(1, changes().apply { next() }.use { it.getLong(0) })

    driver.executeQuery(8, "SELECT * FROM nullability_test", 0).use {
      assertTrue(it.next())
      assertEquals(1, it.getLong(0))
      assertNull(it.getLong(1))
      assertNull(it.getString(2))
      assertNull(it.getBytes(3))
      assertNull(it.getDouble(4))
    }
  }

  @Test fun types_are_correctly_converted_from_JS_to_Kotlin_and_back() {

    val insert = { binders: SqlPreparedStatement.() -> Unit ->
      driver.execute(7, "INSERT INTO nullability_test VALUES (?, ?, ?, ?, ?);", 5, binders)
    }

    insert {
      bindLong(1, 1)
      bindLong(2, Long.MAX_VALUE)
      bindString(3, "Hello")
      bindBytes(4, ByteArray(5) { it.toByte() })
      bindDouble(5, Float.MAX_VALUE.toDouble())
    }

    driver.executeQuery(8, "SELECT * FROM nullability_test", 0).use {
      assertTrue(it.next())
      assertEquals(1, it.getLong(0))
      assertEquals(Long.MAX_VALUE, it.getLong(1))
      assertEquals("Hello", it.getString(2))
      it.getBytes(3)?.forEachIndexed { index, byte -> assertEquals(index.toByte(), byte) }
      assertEquals(Float.MAX_VALUE.toDouble(), it.getDouble(4))
    }
  }

  @Test fun test_get_version_returns_expected_schema_version() {
    assertEquals(1, driver.getVersion())
  }
}


class JsDriverEncryptTest: JsDriverTest() {

  @BeforeTest
  override fun setup() {
    js("var fs = require ('fs');")
    js("if(fs.existsSync('database')) {" +
            " fs.rmSync('database', {recursive: true, force: true})" +
            "}")
    js("fs.mkdirSync('database', {recursive: true})")
    val filePath = js("fs.mkdtempSync('database/db-')")
    val config = FileDatabaseConfiguration("db.sql", filePath + '/', schema, {
      schema.create(it)
    }, { driver, oldVersion, newVersion ->
      schema.migrate(driver, oldVersion, newVersion)
    }, true, "password", "rc4")
    driver = SqlJsCipherDriver(config)
  }
}
