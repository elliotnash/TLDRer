import com.cjcrafter.openai.OpenAI
import com.cjcrafter.openai.chat.ChatMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.ChatRequest
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


private val openAIKey = dotenv()["OPENAI_TOKEN"]

class ChatSummarizer(transcript: String) {
    private val logger = KotlinLogging.logger {}
//    private val prompt = """
//        You are TLDRer, a bot that generates succinct but still informational TLDR summaries for text threads.
//        When you receive a transcript TLDR you generate starts with "TLDR;".
//        You can also answer questions regarding the text threads.
//    """.trimIndent()
    private val prompt = """
        You are TLDRer, a bot that generates succinct but still informational TLDR summaries for text threads.
        Every time you receive a transcript without instruction, you will generate a TLDR.
        Every TLDR you generate starts with "TLDR;".
        You can also answer specific questions regarding the text threads.
        Feel free to use an appropriate level of humor in your responses!
    """.trimIndent()
    private val tldrHeader = "Generate a TLDR for the following text thread:\n"
    private val messages = mutableListOf(
        prompt.toSystemMessage(),
        (tldrHeader+transcript).toUserMessage()
    )
    private var openAI: OpenAI = OpenAI(openAIKey, null, OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build())
    private val request = ChatRequest.builder()
        .model("gpt-3.5-turbo")
        .messages(messages)
        .build()
    private val summaryCompleter = CompletableDeferred<ChatMessage>()
    init {
        openAI.createChatCompletionAsync(request, { response ->
            val message = response[0].message
            // Add message to context
            messages.add(message)
            // Complete request
            summaryCompleter.complete(message)
        }, {
            logger.warn { "OpenAI responded with an Error!" }
            it.printStackTrace()
        })
    }
    suspend fun summary(): ChatMessage {
        return summaryCompleter.await()
    }
    suspend fun question(question: String): ChatMessage {
        val questionCompleter = CompletableDeferred<ChatMessage>()
        messages.add(question.toUserMessage())
        openAI.createChatCompletionAsync(request, { response ->
            val message = response[0].message
            // Add message to context
            messages.add(message)
            // Complete request
            questionCompleter.complete(message)
        }, {
            it.printStackTrace()
        })
        return questionCompleter.await()
    }
}
