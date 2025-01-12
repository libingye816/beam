/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.extensions.python;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Charsets;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.ImmutableList;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility to bootstrap and start a Beam Python service. */
public class PythonService {
  private static final Logger LOG = LoggerFactory.getLogger(PythonService.class);

  private final String module;
  private final List<String> args;

  public PythonService(String module, List<String> args) {
    this.module = module;
    this.args = args;
  }

  public PythonService(String module, String... args) {
    this(module, Arrays.asList(args));
  }

  @SuppressWarnings("argument.type.incompatible")
  public AutoCloseable start() throws IOException, InterruptedException {
    File bootstrapScript = File.createTempFile("bootstrap_beam_venv", ".py");
    bootstrapScript.deleteOnExit();
    try (FileOutputStream fout = new FileOutputStream(bootstrapScript.getAbsolutePath())) {
      ByteStreams.copy(getClass().getResourceAsStream("bootstrap_beam_venv.py"), fout);
    }
    List<String> bootstrapCommand = ImmutableList.of("python", bootstrapScript.getAbsolutePath());
    LOG.info("Running bootstrap command " + bootstrapCommand);
    Process bootstrap =
        new ProcessBuilder("python", bootstrapScript.getAbsolutePath())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    bootstrap.getOutputStream().close();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(bootstrap.getInputStream(), Charsets.UTF_8));
    String lastLine = reader.readLine();
    String lastNonEmptyLine = lastLine;
    while (lastLine != null) {
      LOG.info(lastLine);
      if (lastLine.length() > 0) {
        lastNonEmptyLine = lastLine;
      }
      lastLine = reader.readLine();
    }
    reader.close(); // Make SpotBugs happy.
    int result = bootstrap.waitFor();
    if (result != 0) {
      throw new RuntimeException(
          "Python boostrap failed with error " + result + ", " + lastNonEmptyLine);
    }
    String pythonExecutable = lastNonEmptyLine;
    List<String> command = new ArrayList<>();
    command.add(pythonExecutable);
    command.add("-m");
    command.add(module);
    command.addAll(args);
    LOG.info("Starting python service with arguments " + command);
    Process p =
        new ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .start();
    return p::destroy;
  }

  public static int findAvailablePort() throws IOException {
    ServerSocket s = new ServerSocket(0);
    try {
      return s.getLocalPort();
    } finally {
      s.close();
      try {
        // Some systems don't free the port for future use immediately.
        Thread.sleep(100);
      } catch (InterruptedException exn) {
        // ignore
      }
    }
  }

  public static void waitForPort(String host, int port, int timeoutMs)
      throws TimeoutException, InterruptedException {
    long start = System.currentTimeMillis();
    long duration = 10;
    while (System.currentTimeMillis() - start < timeoutMs) {
      try {
        new Socket(host, port).close();
        return;
      } catch (IOException exn) {
        Thread.sleep(duration);
        duration = (long) (duration * 1.2);
      }
    }
    throw new TimeoutException(
        "Timeout waiting for Python service startup after "
            + (System.currentTimeMillis() - start)
            + " seconds.");
  }
}
