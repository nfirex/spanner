/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package dk.ilios.gauge.options;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

import java.io.File;
import java.util.List;
import java.util.Map;

import dk.ilios.gauge.exception.InvalidCommandException;
import dk.ilios.gauge.util.ShortDuration;

/**
 * Parser for commands coming from the commandline
 */
public final class CommandLineOptions implements GaugeOptions {

    /**
     * Parse the commandline arguments
     * @param args
     * @return
     * @throws InvalidCommandException
     */
    public static CommandLineOptions parse(String[] args) throws InvalidCommandException {
        CommandLineOptions options = new CommandLineOptions();

        CommandLineParser<CommandLineOptions> parser = CommandLineParser.forClass(CommandLineOptions.class);
        try {
            parser.parseAndInject(args, options);
        } catch (InvalidCommandException e) {
            e.setUsage(USAGE);
            throw e;
        }
        return options;
    }

    private CommandLineOptions() {
    }

    // --------------------------------------------------------------------------
    // Dry run -- simple boolean, needs to be checked in some methods
    // --------------------------------------------------------------------------

    @CommandLineParser.Option({"-n", "--dry-run"})
    private boolean dryRun;

    @Override
    public boolean dryRun() {
        return dryRun;
    }

    private void dryRunIncompatible(String optionName) throws InvalidCommandException {
        // This only works because CLP does field injection before method injection
        if (dryRun) {
            throw new InvalidCommandException("Option not available in dry-run mode: " + optionName);
        }
    }

    // --------------------------------------------------------------------------
    // Delimiter -- injected early so methods can use it
    // --------------------------------------------------------------------------

    @CommandLineParser.Option({"-d", "--delimiter"})
    private String delimiter = ",";

    private ImmutableSet<String> split(String string) {
        return ImmutableSet.copyOf(Splitter.on(delimiter).split(string));
    }

    // --------------------------------------------------------------------------
    // Benchmark method names to run
    // --------------------------------------------------------------------------

    private ImmutableSet<String> benchmarkNames = ImmutableSet.of();

    @CommandLineParser.Option({"-b", "--benchmark"})
    private void setBenchmarkNames(String benchmarksString) {
        benchmarkNames = split(benchmarksString);
    }

    @Override
    public ImmutableSet<String> benchmarkMethodNames() {
        return benchmarkNames;
    }

    // --------------------------------------------------------------------------
    // Print configuration?
    // --------------------------------------------------------------------------

    @CommandLineParser.Option({"-p", "--print-config"})
    private boolean printConfiguration = false;

    @Override
    public boolean printConfiguration() {
        return printConfiguration;
    }

    // --------------------------------------------------------------------------
    // Trials
    // --------------------------------------------------------------------------

    private int trials = 1;

    @CommandLineParser.Option({"-t", "--trials"})
    private void setTrials(int trials) throws InvalidCommandException {
        dryRunIncompatible("trials");
        if (trials < 1) {
            throw new InvalidCommandException("trials must be at least 1: " + trials);
        }
        this.trials = trials;
    }

    @Override
    public int trialsPerScenario() {
        return trials;
    }

    // --------------------------------------------------------------------------
    // Time limit
    // --------------------------------------------------------------------------

    private ShortDuration runTime = ShortDuration.of(5, MINUTES);

    @CommandLineParser.Option({"-l", "--time-limit"})
    private void setTimeLimit(String timeLimitString) throws InvalidCommandException {
        try {
            this.runTime = ShortDuration.valueOf(timeLimitString);
        } catch (IllegalArgumentException e) {
            throw new InvalidCommandException("Invalid time limit: " + timeLimitString);
        }
    }

    @Override
    public ShortDuration timeLimit() {
        return runTime;
    }

    // --------------------------------------------------------------------------
    // Run name
    // --------------------------------------------------------------------------

    private String runName = "";

    @CommandLineParser.Option({"-r", "--run-name"})
    private void setRunName(String runName) {
        this.runName = checkNotNull(runName);
    }

    @Override
    public String runName() {
        return runName;
    }

    // --------------------------------------------------------------------------
    // VM specifications
    // --------------------------------------------------------------------------

    private ImmutableSet<String> vmNames = ImmutableSet.of();

    @CommandLineParser.Option({"-m", "--vm"})
    private void setVms(String vmsString) throws InvalidCommandException {
        dryRunIncompatible("vm");
        vmNames = split(vmsString);
    }

    @Override
    public ImmutableSet<String> vmNames() {
        return vmNames;
    }

    // --------------------------------------------------------------------------
    // Measuring instruments to use
    // --------------------------------------------------------------------------

    private static final ImmutableSet<String> DEFAULT_INSTRUMENT_NAMES =
            new ImmutableSet.Builder<String>()
//                    .add("arbitrary") // removed allocation
                    .add("runtime")
                    .build();

    private ImmutableSet<String> instrumentNames = DEFAULT_INSTRUMENT_NAMES;

    @CommandLineParser.Option({"-i", "--instrument"})
    private void setInstruments(String instrumentsString) {
        instrumentNames = split(instrumentsString);
    }

    @Override
    public ImmutableSet<String> instrumentNames() {
        return instrumentNames;
    }

// --------------------------------------------------------------------------
    // Benchmark parameters
    // --------------------------------------------------------------------------

    private Multimap<String, String> mutableUserParameters = ArrayListMultimap.create();

    @CommandLineParser.Option("-D")
    private void addParameterSpec(String nameAndValues) throws InvalidCommandException {
        addToMultimap(nameAndValues, mutableUserParameters);
    }

    @Override
    public ImmutableSetMultimap<String, String> userParameters() {
        // de-dup values, but keep in order
        return new ImmutableSetMultimap.Builder<String, String>()
                .orderKeysBy(Ordering.natural())
                .putAll(mutableUserParameters)
                .build();
    }

    // --------------------------------------------------------------------------
    // VM arguments
    // --------------------------------------------------------------------------

    private Multimap<String, String> mutableVmArguments = ArrayListMultimap.create();

    @CommandLineParser.Option("-J")
    private void addVmArgumentsSpec(String nameAndValues) throws InvalidCommandException {
        dryRunIncompatible("-J");
        addToMultimap(nameAndValues, mutableVmArguments);
    }

    @Override
    public ImmutableSetMultimap<String, String> vmArguments() {
        // de-dup values, but keep in order
        return new ImmutableSetMultimap.Builder<String, String>()
                .orderKeysBy(Ordering.natural())
                .putAll(mutableVmArguments)
                .build();
    }

    // --------------------------------------------------------------------------
    // VM arguments
    // --------------------------------------------------------------------------

    private final Map<String, String> mutableConfigPropertes = Maps.newHashMap();

    @CommandLineParser.Option("-C")
    private void addConfigProperty(String nameAndValue) throws InvalidCommandException {
        List<String> tokens = splitProperty(nameAndValue);
        mutableConfigPropertes.put(tokens.get(0), tokens.get(1));
    }

    @Override
    public ImmutableMap<String, String> configProperties() {
        return ImmutableMap.copyOf(mutableConfigPropertes);
    }

    // --------------------------------------------------------------------------
    // Location of .gauge
    // --------------------------------------------------------------------------

    private File gaugeDirectory = new File(System.getProperty("user.home"), ".gauge");

    @CommandLineParser.Option({"--directory"})
    private void setGaugeDirectory(String path) {
        gaugeDirectory = new File(path);
    }

    @Override
    public File gaugeDirectory() {
        return gaugeDirectory;
    }

    // --------------------------------------------------------------------------
    // Location of config.properties
    // --------------------------------------------------------------------------

    private Optional<File> caliperConfigFile = Optional.absent();

    @CommandLineParser.Option({"-c", "--config"})
    private void setCaliperConfigFile(String filename) {
        caliperConfigFile = Optional.of(new File(filename));
    }

    @Override
    public File gaugeConfigFile() {
        return caliperConfigFile.or(new File(gaugeDirectory, "config.properties"));
    }


    // --------------------------------------------------------------------------
    // Leftover - benchmark class name
    // --------------------------------------------------------------------------

    private String benchmarkClassName;

    @CommandLineParser.Leftovers
    private void setLeftovers(ImmutableList<String> leftovers) throws InvalidCommandException {
        if (leftovers.isEmpty()) {
            throw new InvalidCommandException("No benchmark class specified");
        }
        if (leftovers.size() > 1) {
            throw new InvalidCommandException("Extra stuff, expected only class name: " + leftovers);
        }
        this.benchmarkClassName = leftovers.get(0);
    }

    @Override
    public String benchmarkClassName() {
        return benchmarkClassName;
    }

    // --------------------------------------------------------------------------
    // Helper methods
    // --------------------------------------------------------------------------

    private static List<String> splitProperty(String propertyString) throws InvalidCommandException {
        List<String> tokens = ImmutableList.copyOf(Splitter.on('=').limit(2).split(propertyString));
        if (tokens.size() != 2) {
            throw new InvalidCommandException("no '=' found in: " + propertyString);
        }
        return tokens;
    }

    private void addToMultimap(String nameAndValues, Multimap<String, String> multimap)
            throws InvalidCommandException {
        List<String> tokens = splitProperty(nameAndValues);
        String name = tokens.get(0);
        String values = tokens.get(1);

        if (multimap.containsKey(name)) {
            throw new InvalidCommandException("multiple parameter sets for: " + name);
        }
        multimap.putAll(name, split(values));
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("benchmarkClassName", this.benchmarkClassName())
                .add("benchmarkMethodNames", this.benchmarkMethodNames())
                .add("benchmarkParameters", this.userParameters())
                .add("dryRun", this.dryRun())
                .add("instrumentNames", this.instrumentNames())
                .add("vms", this.vmNames())
                .add("vmArguments", this.vmArguments())
                .add("trials", this.trialsPerScenario())
                .add("printConfig", this.printConfiguration())
                .add("delimiter", this.delimiter)
                .add("gaugeConfigFile", this.caliperConfigFile)
                .toString();
    }

    // --------------------------------------------------------------------------
    // Usage
    // --------------------------------------------------------------------------

    // TODO(kevinb): kinda nice if CommandLineParser could autogenerate most of this...
    // TODO(kevinb): a test could actually check that we don't exceed 79 columns.
    private static final ImmutableList<String> USAGE = ImmutableList.of(
            "Usage:",
            " java com.google.caliper.runner.CaliperMain <benchmark_class_name> [options...]",
            "",
            "Options:",
            " -h, --help         print this message",
            " -n, --dry-run      instead of measuring, execute a single rep for each scenario",
            "                    in-process",
            " -b, --benchmark    comma-separated list of benchmark methods to run; 'foo' is",
            "                    an alias for 'timeFoo' (default: all found in class)",
            " -m, --vm           comma-separated list of VMs to test on; possible values are",
            "                    configured in Caliper's configuration file (default:",
            "                    whichever VM caliper itself is running in, only)",
            " -i, --instrument   comma-separated list of measuring instruments to use; possible ",
            "                    values are configured in Caliper's configuration file ",
            "                    (default: \"" + Joiner.on(",").join(DEFAULT_INSTRUMENT_NAMES) + "\")",
            " -t, --trials       number of independent trials to peform per benchmark scenario; ",
            "                    a positive integer (default: 1)",
            " -l, --time-limit   maximum length of time allowed for a single trial; use 0 to allow ",
            "                    trials to run indefinitely. (default: 30s) ",
            " -r, --run-name     a user-friendly string used to identify the run",
            " -p, --print-config print the effective configuration that will be used by Caliper",
            " -d, --delimiter    separator used in options that take multiple values (default: ',')",
            " -c, --config       location of Caliper's configuration file (default:",
            "                    $HOME/.caliper/config.properties)",
            " --directory        location of Caliper's configuration and data directory ",
            "                    (default: $HOME/.caliper)",
            "",
            " -Dparam=val1,val2,...",
            "     Specifies the values to inject into the 'param' field of the benchmark",
            "     class; if multiple values or parameters are specified in this way, caliper",
            "     will try all possible combinations.",
            "",
            // commented out until this flag is fixed
            // " -JdisplayName='vm arg list choice 1,vm arg list choice 2,...'",
            // "     Specifies alternate sets of VM arguments to pass. As with any variable,",
            // "     caliper will test all possible combinations. Example:",
            // "     -Jmemory='-Xms32m -Xmx32m,-Xms512m -Xmx512m'",
            // "",
            " -CconfigProperty=value",
            "     Specifies a value for any property that could otherwise be specified in ",
            "     $HOME/.caliper/config.properties. Properties specified on the command line",
            "     will override those specified in the file.",
            "",
            "See http://code.google.com/p/caliper/wiki/CommandLineOptions for more details.",
            "");
}