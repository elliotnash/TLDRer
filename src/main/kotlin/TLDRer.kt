import kotlinx.coroutines.runBlocking
import services.signal.SignalService

fun main(args: Array<String>) = runBlocking {
    val chatServices = listOf(
        SignalService()
    )
    // Start all chart services
    for (chatService in chatServices) {
        chatService.start(this)
    }
}
