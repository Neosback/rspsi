package com.rspsi.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Executes only adapter-declared commands from their declared project root. */
public final class ServerBuildRunner {
    public ServerBuildResult run(ServerBuildTask task, Duration timeout) throws IOException {
        return run(task, timeout, line -> { });
    }

    public ServerBuildResult run(ServerBuildTask task, Duration timeout,
                                 java.util.function.Consumer<String> listener) throws IOException {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(listener, "listener");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (!java.nio.file.Files.isDirectory(task.workingDirectory())) {
            throw new IOException("Build working directory does not exist: "
                    + task.workingDirectory());
        }

        Process process = new ProcessBuilder(task.command())
                .directory(task.workingDirectory().toFile())
                .redirectErrorStream(true)
                .start();
        List<String> output = new ArrayList<>();
        Thread reader = new Thread(() -> readOutput(process, output, listener),
                "rspsi-server-build-output");
        reader.setDaemon(true);
        reader.start();
        Instant started = Instant.now();
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Build interrupted", interrupted);
        }
        if (!finished) process.destroyForcibly();
        try {
            reader.join(Math.min(timeout.toMillis(), 2_000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return new ServerBuildResult(finished ? process.exitValue() : -1, !finished,
                Duration.between(started, Instant.now()), output);
    }

    private static void readOutput(Process process, List<String> output,
                                   java.util.function.Consumer<String> listener) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.add(line);
                }
                listener.accept(line);
            }
        } catch (IOException ignored) {
            // The process may be forcibly terminated while its output stream closes.
        }
    }
}
