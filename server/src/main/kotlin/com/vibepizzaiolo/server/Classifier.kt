package com.vibepizzaiolo.server

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StructuredMessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.Sniff
import com.vibepizzaiolo.community.Suggestion
import java.util.Base64
import org.slf4j.LoggerFactory

/** Looks at a photo and says which pizza it is. */
fun interface Classifier {
    /** null: could not tell this time (the app then guesses from the colours itself). */
    fun sniff(jpeg: ByteArray): Sniff?
}

/** What the model is asked to fill in. Field names are the JSON keys. */
class SniffAnswer {
    @JvmField var isPizza: Boolean = true
    /** ROSSA or BIANCA. */
    @JvmField var base: String = ""
    @JvmField var guesses: List<Guess> = emptyList()

    class Guess {
        @JvmField var type: String = ""
        /** 0–100. */
        @JvmField var confidence: Int = 0
    }
}

/** Asks Claude. Used when ANTHROPIC_API_KEY is set. */
class ClaudeClassifier(private val model: String, clientMaker: () -> AnthropicClient = { AnthropicOkHttpClient.fromEnv() }) : Classifier {
    private val log = LoggerFactory.getLogger(ClaudeClassifier::class.java)
    private val client by lazy(clientMaker)

    private val question = """
        This photo was posted to a pizza gallery. Say whether it shows a pizza, whether the sauce is red (ROSSA)
        or white (BIANCA), and which of these types it is: ${PizzaType.entries.joinToString { "${it.name} (${it.label})" }}.
        Give up to three guesses, most likely first, each with a confidence from 0 to 100 that together add up
        to at most 100. Use OTHER when none of the named types fits. If it is not a pizza, set isPizza to false
        and leave the guesses empty.
    """.trimIndent()

    /** The request: the photo, the question, and the shape the answer must have. */
    fun request(jpeg: ByteArray): StructuredMessageCreateParams<SniffAnswer> {
        val image = ImageBlockParam.builder()
            .source(Base64ImageSource.builder().mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).data(Base64.getEncoder().encodeToString(jpeg)).build())
            .build()
        return MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            .outputConfig(SniffAnswer::class.java)
            .addUserMessageOfContentBlockParams(listOf(ContentBlockParam.ofImage(image), ContentBlockParam.ofText(TextBlockParam.builder().text(question).build())))
            .build()
    }

    override fun sniff(jpeg: ByteArray): Sniff? = try {
        val message = client.messages().create(request(jpeg))
        // a refusal or a cut-off answer carries no usable JSON
        val answer = message.content().firstNotNullOfOrNull { it.text().orElse(null) }?.text()
        answer?.let(::toSniff)
    } catch (e: Exception) {
        log.warn("could not classify a photo: {}", e.toString())
        null
    }

    companion object {
        fun toSniff(a: SniffAnswer): Sniff {
            val guesses = a.guesses.mapNotNull { g ->
                PizzaType.entries.firstOrNull { it.name == g.type.trim().uppercase() }?.let { Suggestion(it, g.confidence.coerceIn(1, 99)) }
            }.distinctBy { it.type }.sortedByDescending { it.confidence }.take(3)
            return Sniff(
                suggestions = if (a.isPizza) guesses else emptyList(),
                base = Base.entries.firstOrNull { it.name == a.base.trim().uppercase() && it != Base.BOTH },
                isPizza = a.isPizza,
                source = "vision",
            )
        }
    }
}
