package org.goobs.truth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * A collection of properties
 *
 * @author Gabor Angeli
 */
public class Props {
  private static final Redwood.RedwoodChannels logger = Redwood.channels("Exec");

  @Option(name="reverb.raw.dir", gloss="The raw directory where Reverb outputs are stored")
  public static File REVERB_RAW_DIR = new File("/scr/nlp/data/openie/output/reverb1.4/clueweb_english");
  @Option(name="reverb.raw.gzip", gloss="If true, the input files are in gzip format")
  public static boolean REVERB_RAW_GZIP = true;

  private static void initializeAndValidate() {
    /* nothing yet */
  }

  public static void exec(final Function<Properties, Object> toRun, String[] args) {
    // Set options classes
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    Execution.optionClasses = new Class<?>[stackTrace.length];
    for (int i=0; i<stackTrace.length; ++i) {
      try {
        Execution.optionClasses[i] = Class.forName(stackTrace[i].getClassName());
      } catch (ClassNotFoundException e) { logger.fatal(e); }
    }
    // Start Program
    if (args.length == 1) {
      // Case: Run with TypeSafe Config file
      if (!new File(args[0]).canRead()) logger.fatal("Cannot read typesafe-config file: " + args[0]);
      Config config = null;
      try {
        config = ConfigFactory.parseFile(new File(args[0]).getCanonicalFile()).resolve();
      } catch (IOException e) {
        System.err.println("Could not find config file: " + args[0]);
        System.exit(1);
      }
      final Properties props = new Properties();
      for (Map.Entry<String, ConfigValue> entry : config.entrySet()) {
        String candidate = entry.getValue().unwrapped().toString();
        if (candidate != null) {
          props.setProperty(entry.getKey(), candidate);
        }
      }
      Execution.exec(new Runnable() { @Override public void run() {
        initializeAndValidate();
        toRun.apply(props);
      } }, config);
    } else {
      // Case: Run with Properties file or command line arguments
      final Properties props = StringUtils.argsToProperties(args);
      Execution.exec(new Runnable() { @Override public void run() {
        Props.initializeAndValidate();
        toRun.apply(props);
      } }, props);
    }
  }
}
