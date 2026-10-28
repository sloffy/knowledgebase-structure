package com.polytech.knowledgebase.structure.console;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConsoleStreamsTest {
    @Test
    void redirectedStreamsUseUtf8ForRussianInputAndOutput() throws IOException {
        var output = new ByteArrayOutputStream();
        var streams = ConsoleStreams.open(null,
                new ByteArrayInputStream("Что нужно новичку?\nвыход\n".getBytes(StandardCharsets.UTF_8)), output);
        assertThat(streams.reader().readLine()).isEqualTo("Что нужно новичку?");
        assertThat(streams.reader().readLine()).isEqualTo("выход");
        assertThat(streams.reader().readLine()).isNull();
        streams.writer().println("База знаний");
        streams.writer().flush();
        assertThat(output.toByteArray()).isEqualTo(("База знаний" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void interactiveStreamsRespectCp866InsteadOfForcingUtf8() throws IOException {
        Charset terminalCharset = Charset.forName("IBM866");
        var terminalOutput = new ByteArrayOutputStream();
        Console console = mock(Console.class);
        when(console.reader()).thenReturn(new InputStreamReader(
                new ByteArrayInputStream("выход\n".getBytes(terminalCharset)), terminalCharset));
        when(console.writer()).thenReturn(new PrintWriter(new OutputStreamWriter(terminalOutput, terminalCharset), true));
        var redirectedOutput = new ByteArrayOutputStream();
        var streams = ConsoleStreams.open(console, InputStream.nullInputStream(), redirectedOutput);
        assertThat(streams.reader().readLine()).isEqualTo("выход");
        streams.writer().println("База знаний");
        streams.writer().flush();
        assertThat(terminalOutput.toByteArray()).isEqualTo(("База знаний" + System.lineSeparator()).getBytes(terminalCharset));
        assertThat(redirectedOutput.size()).isZero();
    }
}
