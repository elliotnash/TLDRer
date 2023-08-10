import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import services.ChatMessage
import services.ChatService
import services.signal.SignalService
import kotlin.math.max
import kotlin.math.min


class Command(
    vararg val commands: String,
    val onCommand: suspend (service: ChatService, message: ChatMessage, content: String?) -> Unit
)

class TLDRer {
    private val logger = KotlinLogging.logger {}

    private val summarizers = mutableMapOf<String, ChatSummarizer>()

    private val chatServices = listOf(
        SignalService("+12508807560")
    )

    private val commands = listOf(
        Command("!search") { service, message, content ->
            if (content != null) {
                // Looking up conversations is service dependant currently.
                // Currently only implemented for Signal
                if (service is SignalService) {
                    val cid = service.lookupConversationId(content)
                    service.sendMessage(message.conversationId, "Found conversation ID: $cid")
                }
            }
        },
        Command("!tldr") { service, message, content ->
            // Process the command arguments to fetch the limit and conversation name
            var conversationName: String? = null
            var limit: Int? = null
            if (!content.isNullOrBlank()) {
                val args = content.split(" ")
                val firstInt = args.firstOrNull()?.toIntOrNull()
                val lastInt = args.lastOrNull()?.toIntOrNull()

                if (firstInt != null) {
                    limit = firstInt
                    conversationName = args.drop(1).joinToString(" ")
                } else if (lastInt != null) {
                    limit = lastInt
                    conversationName = args.dropLast(1).joinToString(" ")
                } else {
                    conversationName = content
                }
            }
            // Clamp limit between 1 and 500
            if (limit != null) {
                limit = min(max(limit, 1), 500)
            }

            // We'll use the current chat as the conversationId unless specified with conversationName
            var conversationId = message.conversationId
            if (conversationName != null) {
                // Looking up conversations is service dependant currently.
                // Currently only implemented for Signal
                if (service is SignalService) {
                    val id = service.lookupConversationId(conversationName)
                    if (id == null) {
                        service.sendMessage(message.conversationId, "Sorry, that user/group could not be found!")
                    }
                    conversationId = id!!
                }
            }

            val lastMessage = service.getLastMessage(conversationId, message.senderId, message.timestamp)
            val since = if (limit != null) {
                null
            } else {
                lastMessage?.timestamp
            }
            val msgHistory = service.getMessages(conversationId, since=since, before=message.timestamp, limit=limit)

            var transcript = ""
            for (msg in msgHistory) {
                val msgContent = if (msg.text != null) {
                    msg.text
                } else if (msg.attachmentsInfo != null) {
                    msg.attachmentsInfo
                } else if (msg.reactionEmoji != null) {
                    "${msg.reactionEmoji} to '${msg.reactionText ?: "Unknown message"}'"
                } else {
                    "Unknown message"
                }
                transcript += "${msg.senderName}: \"${msgContent}\"\n"
            }
            transcript = transcript.trim()

            val summarizer = ChatSummarizer(transcript)
            summarizers[message.conversationId] = summarizer
            val summary = summarizer.summary()

            service.sendMessage(message.conversationId, summary.content)
        },
        Command("!question", "!q") { service, message, question ->
            if (question == null) {
                service.sendMessage(message.conversationId, "Sorry, you must specify a question to ask!")
                return@Command
            }

            val summarizer = summarizers[message.conversationId]
            if (summarizer == null) {
                service.sendMessage(message.conversationId, "Sorry, you can't use the !question command without first generating a TLDR!")
            } else {
                val response = summarizer.question(question)
                service.sendMessage(message.conversationId, response.content)
            }
        }
    )

    fun start(scope: CoroutineScope) {
        // Start all chart services
        for (chatService in chatServices) {
            chatService.start(scope)
            chatService.addMessageListener { message ->
                coroutineScope {
                    launch {
                        onMessage(chatService, message)
                    }
                    launch {
                        handleCommands(chatService, message)
                    }
                }
            }
        }
    }

    private suspend fun handleCommands(service: ChatService, message: ChatMessage) {
        // Don't respond to messages sent by the bot
        // Or reactions
        if (!message.fromBot && message.text != null) {
            // Split message into command and content
            val commandParts = message.text.split(" ", limit=2)
            val commandString = commandParts.firstOrNull()
            val content = if (commandParts.size == 2) {
                commandParts[1]
            } else {
                null
            }

            if (!commandString.isNullOrBlank()) {
                val commandMatch = commandString.lowercase()
                for (command in commands) {
                    if (command.commands.map{it.lowercase()}.contains(commandMatch)) {
                        command.onCommand(service, message, content)
                    }
                }
            }
        }
    }

    private suspend fun onMessage(service: ChatService, message: ChatMessage) {

    }
}

fun main(args: Array<String>) = runBlocking {
    val tldrer = TLDRer()
    tldrer.start(this)
}
