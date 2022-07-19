package com.github.julianhille.sqldelight.drivers.jscipher

import com.squareup.sqldelight.Query
import com.squareup.sqldelight.db.SqlCursor
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.internal.Atomic
import com.squareup.sqldelight.internal.copyOnWriteList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsQueryTest {

  private val mapper = { cursor: SqlCursor ->
    TestData(
      cursor.getLong(0)!!, cursor.getString(1)!!
    )
  }

  private val schema = object : SqlDriver.Schema {
    override val version: Int = 1

    override fun create(driver: SqlDriver) {
      driver.execute(
        null,
        """
              CREATE TABLE test (
                id INTEGER NOT NULL PRIMARY KEY,
                value TEXT NOT NULL
               );
        """.trimIndent(),
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

  private lateinit var driver: SqlDriver

  @BeforeTest
  fun setup() {
    var config = DatabaseConfiguration(":memory:", "", schema, {
      schema.create(it)
    }, { driver, oldVersion, newVersion ->
      schema.migrate(driver, oldVersion, newVersion)
    }
    )
    driver = SqlJsCipherDriver(config)
  }

  @AfterTest
  fun tearDown() {
    driver.close()
  }

  @Test fun executeAsOne() {

    val data1 = TestData(1, "val1")
    driver.insertTestData(data1)

    assertEquals(data1, driver.testDataQuery().executeAsOne())
  }

  @Test fun executeAsOneTwoTimes() {

    val data1 = TestData(1, "val1")
    driver.insertTestData(data1)

    val query = driver.testDataQuery()

    assertEquals(query.executeAsOne(), query.executeAsOne())
  }

  @Test fun executeAsOneThrowsNpeForNoRows() {
    assertFailsWith<NullPointerException> {
      driver.testDataQuery().executeAsOne()
    }
  }

  @Test fun executeAsOneThrowsIllegalStateExceptionForManyRows() {
    assertFailsWith<IllegalStateException> {
      driver.insertTestData(TestData(1, "val1"))
      driver.insertTestData(TestData(2, "val2"))

      driver.testDataQuery().executeAsOne()
    }
  }

  @Test fun executeAsOneOrNull() {

    val data1 = TestData(1, "val1")
    driver.insertTestData(data1)

    val query = driver.testDataQuery()
    assertEquals(data1, query.executeAsOneOrNull())
  }

  @Test fun executeAsOneOrNullReturnsNullForNoRows() {
    assertNull(driver.testDataQuery().executeAsOneOrNull())
  }

  @Test fun executeAsOneOrNullThrowsIllegalStateExceptionForManyRows() {
    assertFailsWith<IllegalStateException> {
      driver.insertTestData(TestData(1, "val1"))
      driver.insertTestData(TestData(2, "val2"))

      driver.testDataQuery().executeAsOneOrNull()
    }
  }

  @Test fun executeAsList() {

    val data1 = TestData(1, "val1")
    val data2 = TestData(2, "val2")

    driver.insertTestData(data1)
    driver.insertTestData(data2)

    assertEquals(listOf(data1, data2), driver.testDataQuery().executeAsList())
  }

  @Test fun executeAsListForNoRows() {
    assertTrue(driver.testDataQuery().executeAsList().isEmpty())
  }

  @Test fun notifyDataChangedNotifiesListeners() {

    val notifies = Atomic(0)
    val query = driver.testDataQuery()
    val listener = object : Query.Listener {
      override fun queryResultsChanged() {
        notifies.increment()
      }
    }

    query.addListener(listener)
    assertEquals(0, notifies.get())

    query.notifyDataChanged()
    assertEquals(1, notifies.get())
  }

  @Test fun removeListenerActuallyRemovesListener() {

    val notifies = Atomic(0)
    val query = driver.testDataQuery()
    val listener = object : Query.Listener {
      override fun queryResultsChanged() {
        notifies.increment()
      }
    }

    query.addListener(listener)
    query.removeListener(listener)
    query.notifyDataChanged()
    assertEquals(0, notifies.get())
  }

  private fun SqlDriver.insertTestData(testData: TestData) {
    execute(1, "INSERT INTO test VALUES (?, ?)", 2) {
      bindLong(1, testData.id)
      bindString(2, testData.value)
    }
  }

  private fun SqlDriver.testDataQuery(): Query<TestData> {
    return object : Query<TestData>(copyOnWriteList(), mapper) {
      override fun execute(): SqlCursor {
        return executeQuery(0, "SELECT * FROM test", 0)
      }
    }
  }

  private data class TestData(val id: Long, val value: String)
}

// Not actually atomic, the type needs to be as the listeners get frozen.
private fun Atomic<Int>.increment() = set(get() + 1)
