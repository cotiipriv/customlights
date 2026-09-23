package com.cotii.customlights.client.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** One word of any characters except spaces ({@code #FF8800}, {@code ~1.5}, {@code tag:boss}, light ids). */
public final class TokenArgument implements ArgumentType<String> {
    private final Supplier<Collection<String>> suggestions;

    private TokenArgument(Supplier<Collection<String>> suggestions) {
        this.suggestions = suggestions;
    }

    public static TokenArgument token(Supplier<Collection<String>> suggestions) {
        return new TokenArgument(suggestions);
    }

    public static TokenArgument token(String... suggestions) {
        List<String> list = List.of(suggestions);
        return new TokenArgument(() -> list);
    }

    public static String get(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String suggestion : suggestions.get()) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        // No examples: brigadier would report every literal next to a token as ambiguous
        return List.of();
    }
}
