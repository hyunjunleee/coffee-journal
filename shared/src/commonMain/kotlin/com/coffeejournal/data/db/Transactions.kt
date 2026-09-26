package com.coffeejournal.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs a block as one SQLite write transaction. Every DAO / repository call made inside the block (in the same
 * coroutine) joins it, so either all of its writes are committed or, when the block throws, none are.
 */
interface TransactionRunner {
    suspend fun <R> write(block: suspend () -> R): R
}

/** Room KMP writer transaction; nested use (a DAO @Transaction method inside the block) becomes a savepoint. */
class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <R> write(block: suspend () -> R): R =
        db.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
}
