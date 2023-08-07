package services.signal

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object SignalDatabase {
    init {
        Database.connect("jdbc:sqlite:signal.db", "org.sqlite.JDBC")
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(SignalMessages)
        }
    }

    fun addMessage(message: SignalMessage) {
        transaction {
            try {
                SignalMessages.replace {
                    it[timestamp] = message.timestamp
                    it[sourceNumber] = message.sourceNumber
                    it[sourceName] = message.sourceName
                    it[conversationNumber] = message.conversationNumber
                    it[SignalMessages.message] = message.message
                    it[fromSelf] = message.fromSelf
                    it[quoteId] = message.quoteId
                    it[quoteText] = message.quoteText
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

object SignalMessages : Table("messages") {
    val timestamp: Column<Long> = long("timestamp").uniqueIndex()
    val sourceNumber: Column<String> = text("source_number")
    val sourceName: Column<String> = text("source_name")
    val conversationNumber: Column<String> = text("conversation_number")
    val message: Column<String> = text("message")
    val fromSelf: Column<Boolean> = bool("from_self")
    val quoteId: Column<Long?> = long("quote_id").nullable()
    val quoteText: Column<String?> = text("quote_text").nullable()
}
