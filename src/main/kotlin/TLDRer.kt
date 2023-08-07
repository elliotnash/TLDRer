import kotlinx.coroutines.runBlocking
import services.signal.SignalService

fun main(args: Array<String>) = runBlocking {
    val chatServices = listOf(
        SignalService("+12508807560")
    )
    // Start all chart services
    for (chatService in chatServices) {
        chatService.start(this)
        chatService.addMessageListener {
            println("Received new message: $it")
        }
    }
}
