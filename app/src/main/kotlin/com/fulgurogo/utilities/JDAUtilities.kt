package com.fulgurogo.utilities

import com.fulgurogo.Config
import com.fulgurogo.features.exam.ExamPlayer
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook

fun JDA.userName(hunter: ExamPlayer): String = userName(hunter.discordId)
fun JDA.userName(discordId: String): String {
    val discordUser = getUserById(discordId)
    val guild = getGuildById(Config.Bot.GUILD_ID)
    return discordUser?.let {
        guild?.getMember(it)?.effectiveName ?: it.name
    } ?: discordId
}

fun JDA.publicMessage(channelId: String, message: String, title: String = "") =
    getTextChannelById(channelId)
        ?.sendMessageEmbeds(
            EmbedBuilder()
                .setColor(Config.Bot.EMBED_COLOR)
                .apply { if (title.isNotBlank()) setTitle(title) }
                .setDescription(if (message.length > 2048) message.substring(0, 2045) + "..." else message)
                .build()
        )
        ?.queue()

fun acknowledge(event: SlashCommandInteractionEvent): InteractionHook = event.let {
    it.deferReply(true).queue()
    it.hook.setEphemeral(true)
}

fun simpleMessage(hook: InteractionHook, emoji: String, title: String, description: String) =
    sendMessage(hook, "$emoji    __${title}__    $emoji", description)

fun simpleError(hook: InteractionHook, emoji: String, errorMessage: String) =
    sendMessage(hook, "$emoji    __Erreur__    $emoji", ":negative_squared_cross_mark: $errorMessage")

private fun sendMessage(hook: InteractionHook, title: String, description: String) = hook.sendMessageEmbeds(
    EmbedBuilder().setColor(Config.Bot.EMBED_COLOR).setTitle(title).setDescription(description.ellipsize(2048)).build()
).queue()
