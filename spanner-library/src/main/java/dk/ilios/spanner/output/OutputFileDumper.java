/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.ilios.spanner.output;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.logging.Logger;

import dk.ilios.spanner.internal.InvalidBenchmarkException;
import dk.ilios.spanner.model.Trial;

import static java.util.logging.Level.SEVERE;

/**
 * {@link ResultProcessor} that dumps the output data to a file in JSON format. By default, the
 * output will be dumped to a file called
 * {@code /sdcard/spanner/results/[benchmark classname].[timestamp].json}; if it exists and is a file,
 * the file will be overwritten.  The location can be overridden as either a file or a directory
 * using either the {@code file} or {@code dir} options respectively.
 */
public final class OutputFileDumper implements ResultProcessor {

    private static final Logger logger = Logger.getLogger(OutputFileDumper.class.getName());

    private final Gson gson;
    private final File resultFile;
    private final File workFile;
    private Optional<JsonWriter> writer = Optional.absent();

    public OutputFileDumper(Gson gson, File resultFile) throws InvalidBenchmarkException {
        this.resultFile = resultFile;
        logger.fine(String.format("using %s for results", resultFile));
        this.gson = gson;
        this.workFile = new File(resultFile.getPath() + ".tmp");
    }

    @Override
    public void processTrial(Trial trial) {
        if (!writer.isPresent()) {
            try {
                Files.createParentDirs(workFile);
                JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(workFile), Charsets.UTF_8));
                writer.setIndent("  ");  // always pretty print
                writer.beginArray();
                this.writer = Optional.of(writer);
            } catch (IOException e) {
                logger.log(SEVERE, String.format(
                        "An error occured writing trial %s. Results in %s will be incomplete.", trial.id(),
                        resultFile), e);
            }
        }
        if (writer.isPresent()) {
            gson.toJson(trial, Trial.class, writer.get());
        }
    }

    @Override
    public void close() throws IOException {
        if (writer.isPresent()) {
            writer.get().endArray().close();
        }
        if (workFile.exists()) {
            Files.move(workFile, resultFile);
        }
    }
}
