package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    suspend fun insert(transaction: TransactionEntity) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun update(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    fun getTransactionsInRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsInRange(startMillis, endMillis)
    }
}
