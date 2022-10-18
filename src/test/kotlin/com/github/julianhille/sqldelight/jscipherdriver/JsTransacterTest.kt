package com.github.julianhille.sqldelight.drivers.jscipher

import com.squareup.sqldelight.Transacter
import com.squareup.sqldelight.TransacterImpl
import com.squareup.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class JsTransacterTest {

  private val schema = object : SqlDriver.Schema {
    override val version = 1
    override fun create(driver: SqlDriver) {}
    override fun migrate(
      driver: SqlDriver,
      oldVersion: Int,
      newVersion: Int
    ) {
    }
  }

  private lateinit var transacter: Transacter
  private lateinit var driver: SqlDriver

  @BeforeTest
  fun setup() {
    var config = MemoryDatabaseConfiguration(schema, {
      schema.create(it)
    }, { driver, oldVersion, newVersion ->
      schema.migrate(driver, oldVersion, newVersion)
    })
    driver = SqlJsCipherDriver(config)
    transacter = object: TransacterImpl(driver) {}
  }

  @AfterTest
  fun teardown() {
    driver.close()
  }

  @Test fun afterCommit_runs_after_transaction_commits() {
    var counter = 0
    transacter.transaction {
      afterCommit { counter++ }
      assertEquals(0, counter)
    }

    assertEquals(1, counter)
  }

  @Test fun afterCommit_does_not_run_after_transaction_rollbacks() {
    var counter = 0
    transacter.transaction {
      afterCommit { counter++ }
      assertEquals(0, counter)
      rollback()
    }

    assertEquals(0, counter)
  }

  @Test fun afterCommit_runs_after_enclosing_transaction_commits() {
    var counter = 0
    transacter.transaction {
      afterCommit { counter++ }
      assertEquals(0, counter)

      transaction {
        afterCommit { counter++ }
        assertEquals(0, counter)
      }

      assertEquals(0, counter)
    }

    assertEquals(2, counter)
  }

  @Test fun afterCommit_does_not_run_in_nested_transaction_when_enclosing_rolls_back()  { 
    var counter = 0
    transacter.transaction {
      afterCommit { counter++ }
      assertEquals(0, counter)

      transaction {
        afterCommit { counter++ }
      }

      rollback()
    }

    assertEquals(0, counter)
  }

  @Test fun afterCommit_does_not_run_in_nested_transaction_when_nested_rolls_back() { 
    var counter = 0
    transacter.transaction {
      afterCommit { counter++ }
      assertEquals(0, counter)

      transaction {
        afterCommit { counter++ }
        rollback()
      }

      throw AssertionError()
    }

    assertEquals(0, counter)
  }

  @Test fun afterRollback_no_ops_if_the_transaction_never_rolls_back() {
    var counter = 0
    transacter.transaction {
      afterRollback { counter++ }
    }

    assertEquals(0, counter)
  }

  @Test fun afterRollback_runs_after_a_rollback_occurs() {
    var counter = 0
    transacter.transaction {
      afterRollback { counter++ }
      rollback()
    }

    assertEquals(1, counter)
  }

  @Test fun afterRollback_runs_after_an_inner_transaction_rolls_back() {
    var counter = 0
    transacter.transaction {
      afterRollback { counter++ }
      transaction {
        rollback()
      }
      throw AssertionError()
    }

    assertEquals(1, counter)
  }

  @Test fun afterRollback_runs_in_an_inner_transaction_when_the_outer_transaction_rolls_back() {
    var counter = 0
    transacter.transaction {
      transaction {
        afterRollback { counter++ }
      }
      rollback()
    }

    assertEquals(1, counter)
  }

  @Test fun transactions_close_themselves_out_properly() {
    var counter = 0
    transacter.transaction {
      afterCommit { counter++ }
    }

    transacter.transaction {
      afterCommit { counter++ }
    }

    assertEquals(2, counter)
  }

  @Test fun setting_no_enclosing_fails_if_there_is_a_currently_running_transaction() {
    transacter.transaction(noEnclosing = true) {
      assertFailsWith<IllegalStateException> {
        transacter.transaction(noEnclosing = true) {
          throw AssertionError()
        }
      }
    }
  }

  @Test fun an_exception_thrown_in_postRollback_function_is_combined_with_the_exception_in_the_main_body() {
    class ExceptionA : RuntimeException()
    class ExceptionB : RuntimeException()
    try {
      transacter.transaction {
        afterRollback {
          throw ExceptionA()
        }
        throw ExceptionB()
      }
      fail("Should have thrown!")
    } catch (e: Throwable) {
      assertTrue("Exception thrown in body not in message($e)") { e.toString().contains("ExceptionA") }
      assertTrue("Exception thrown in rollback not in message($e)") { e.toString().contains("ExceptionB") }
    }
  }
}
