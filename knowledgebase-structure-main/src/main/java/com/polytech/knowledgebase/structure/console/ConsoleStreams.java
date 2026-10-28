package com.polytech.knowledgebase.structure.console;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Uses the terminal's encoding interactively and UTF-8 for redirected streams. */
record ConsoleStreams(BufferedReader reader, PrintWriter writer) {
    static ConsoleStreams open(Console console, InputStream input, OutputStream output) {
        if (console != null) {
            return new ConsoleStreams(new BufferedReader(console.reader()), console.writer());
        }
        return new ConsoleStreams(
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)),
                new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true));
    }
}
