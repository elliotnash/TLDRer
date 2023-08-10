package org.elliotnash.services.signal

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
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
        try {
            if (message.isReactionRemove) {
                // If it's a reaction remove we should remove the entry
                // from the database instead of adding it
                transaction {
                    SignalMessages.deleteWhere {
                        (reactionTarget eq message.reactionTarget) and
                        (reactionEmoji eq message.reactionEmoji) and
                        (sourceNumber eq message.sourceNumber)
                    }
                }
            } else if (message.remoteDelete != null) {
                // If it has a remoteDelete key, we should remove the message
                // from the database instead of adding it
                transaction {
                    SignalMessages.deleteWhere {
                        timestamp eq message.remoteDelete
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
                        it[attachmentsInfo] = message.attachmentsInfo
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun getMessage(timestamp: Long): SignalMessage? {
        return transaction {
            SignalMessages.select {
                SignalMessages.timestamp eq timestamp
            }.map {
                SignalMessage.fromResultRow(it)
            }.firstOrNull()
        }
    }

    fun getMessages(conversationNumber: String, since: Long? = null, before: Long? = null, limit: Int? = null): List<SignalMessage> {
        return transaction {
            var selection = (SignalMessages.conversationNumber eq conversationNumber)
            if (before != null) {
                selection = selection and (SignalMessages.timestamp less before)
            }
            if (since != null) {
                selection = selection and (SignalMessages.timestamp greater since)
            }

            return@transaction SignalMessages.select { selection }
                .orderBy(SignalMessages.timestamp to SortOrder.DESC)
                .limit(limit ?: 250).map {
                    SignalMessage.fromResultRow(it)
                }.sortedBy { it.timestamp }
        }
    }

    fun getLastMessage(conversationId: String, senderId: String, before: Long?): SignalMessage? {
        var selection = (SignalMessages.conversationNumber eq conversationId) and
                (SignalMessages.sourceNumber eq senderId) and
                (SignalMessages.reactionTarget.isNull())
        if (before != null) {
            selection = selection and (SignalMessages.timestamp less before)
        }
        return transaction {
            SignalMessages.select { selection }
                .orderBy(SignalMessages.timestamp to SortOrder.DESC)
                .limit(1)
                .map {
                    SignalMessage.fromResultRow(it)
                }
        }.firstOrNull()
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
    val attachmentsInfo: Column<String?> = text("attachments_info").nullable()
}
