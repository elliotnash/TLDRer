package services.signal

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import services.signal.SignalMessages.reactionEmoji
import services.signal.SignalMessages.reactionTarget
import services.signal.SignalMessages.sourceNumber

object SignalDatabase {
    init {
        Database.connect("jdbc:sqlite:signal.db", "org.sqlite.JDBC")
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(SignalMessages)
        }
    }

    fun addMessage(message: SignalMessage) {
        try {
            if (message.isReactionRemove == true) {
                // If it's a reaction remove we should remove the entry
                // from the database instead of adding it
                transaction {
                    SignalMessages.deleteWhere {
                        (reactionTarget eq message.reactionTarget) and
                        (reactionEmoji eq message.reactionEmoji) and
                        (sourceNumber eq message.sourceNumber)
                    }
                }
            } else {
                if (message.reactionTarget != null) {
                    // If this is a reaction, we need to delete any reactions by
                    // the given user to the same message before inserting
                    transaction {
                        SignalMessages.deleteWhere {
                            (reactionTarget eq message.reactionTarget) and
                            (sourceNumber eq message.sourceNumber)
                        }
                    }
                }
                // Insert the actual message
                transaction {
                    SignalMessages.replace {
                        it[timestamp] = message.timestamp
                        it[sourceNumber] = message.sourceNumber
                        it[sourceName] = message.sourceName
                        it[conversationNumber] = message.conversationNumber
                        it[SignalMessages.message] = message.message
                        it[fromSelf] = message.fromSelf
                        it[fromBot] = message.fromBot
                        it[quoteId] = message.quoteId
                        it[quoteText] = message.quoteText
                        it[reactionEmoji] = message.reactionEmoji
                        it[reactionTarget] = message.reactionTarget
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}

object SignalMessages : Table("messages") {
    val timestamp: Column<Long> = long("timestamp").uniqueIndex()
    val sourceNumber: Column<String> = text("source_number")
    val sourceName: Column<String> = text("source_name")
    val conversationNumber: Column<String> = text("conversation_number")
    val message: Column<String?> = text("message").nullable()
    val fromSelf: Column<Boolean> = bool("from_self")
    val fromBot: Column<Boolean> = bool("from_bot")
    val quoteId: Column<Long?> = long("quote_id").nullable()
    val quoteText: Column<String?> = text("quote_text").nullable()
    val reactionEmoji: Column<String?> = text("reaction_emoji").nullable()
    val reactionTarget: Column<Long?> = long("reaction_target").nullable()
}
