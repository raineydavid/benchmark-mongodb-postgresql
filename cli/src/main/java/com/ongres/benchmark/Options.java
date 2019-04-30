package com.ongres.benchmark;

import com.ongres.benchmark.config.model.Config;
import com.ongres.benchmark.config.model.target.Target;
import com.ongres.benchmark.config.model.target.TargetDatabase;

import java.io.File;

import picocli.CommandLine.Help;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

public abstract class Options {

  @Mixin
  private final Config config;

  @Option(names = {"-c", "--config"},
      description = "Configuration file.",
      required = false,
      showDefaultValue = Help.Visibility.NEVER)
  private File configFile;

  @Option(names = {"-h", "--help"}, usageHelp = true,
      description = "Displays this help message and quits.")
  private boolean helpRequested = false;

  @Option(names = {"-v", "--version"}, description = "Show version and exit", versionHelp = true)
  private boolean versionRequested = false;

  @Option(names = {"--test"}, description = "Test command line", hidden = true)
  private boolean test = false;
  
  @ParentCommand
  protected App app;
  
  private final Target target;

  @Mixin
  private final TargetDatabase targetDatabase;

  protected Options(Config config) {
    this.config = config;
    this.target = config.getTarget();
    this.targetDatabase = target.getDatabase();
  }
  
  protected Config getConfig() {
    return config;
  }
  
  protected File getConfigFile() {
    return configFile;
  }
  
  public boolean isHelpRequested() {
    return helpRequested;
  }

  public boolean isVersionRequested() {
    return versionRequested;
  }

  public boolean isTest() {
    return test;
  }
}
