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

  private static String checkEnv(String key, String defaultValue) {
    String value = System.getenv().get(key);
    if (value == null) { return defaultValue; }
    return value;
  }

  @Option(name="psql.host", gloss="The hostname for the PSQL server")
  public static String PSQL_HOST = checkEnv("PGHOST", "john0");
  @Option(name="psql.port", gloss="The port at which postgres is running")
  public static int PSQL_PORT = Integer.parseInt(checkEnv("PGPORT", "4243"));
  @Option(name="psql.db", gloss="The database name")
  public static String PSQL_DB = checkEnv("DBNAME", "truth");
  @Option(name="psql.username", gloss="The username for the postgres session")
  public static String PSQL_USERNAME = checkEnv("PGUSER", "gabor");
  @Option(name="psql.password", gloss="The password for the postgres session")
  public static String PSQL_PASSWORD = checkEnv("PGPASSWORD", "gabor");

  @Option(name="natlog.indexer.lazy", gloss="If true, do word indexing lazily rather than reading the indexer at once")
  public static boolean NATLOG_INDEXER_LAZY = false;

  @Option(name="server.host", gloss="The hostname for the inference server")
  public static String SERVER_HOST = "localhost";
  @Option(name="server.port", gloss="The hostname for the inference server")
  public static int SERVER_PORT = 1337;

  @Option(name="search.timeout", gloss="The maximum number of ticks to run for on the server")
  public static long SEARCH_TIMEOUT = 100000;

  @Option(name="natlog.ollie.jar", gloss="The path to the Ollie *.jar file")
  public static File NATLOG_OLLIE_JAR = new File("etc/ollie.jar");
  @Option(name="natlog.ollie.port", gloss="The port to start an Ollie server on")
  public static int NATLOG_OLLIE_PORT = 4299;
  @Option(name="natlog.ollie.malt.path", gloss="The path to the Malt parser serialized model")
  public static File NATLOG_OLLIE_MALT_PATH = new File("etc/engmalt.linear-1.7.mco");

  @Option(name="data.fracas.path", gloss="The path to the FraCaS test suite")
  public static File DATA_FRACAS_PATH = new File("etc/fracas.xml");

  @Option(name="learn.iterations", gloss="The number of iterations to run learning for")
  public static int LEARN_ITERATIONS = 100;
  @Option(name="learn.model", gloss="The file to save/load the model from")
  public static File LEARN_MODEL = new File("/dev/null");
  @Option(name="learn.resume.do", gloss="If true, resume training from a saved model")
  public static boolean LEARN_RESUME_DO = false;
  @Option(name="learn.resume.model", gloss="The path to the model to resume training from")
  public static File LEARN_RESUME_MODEL = new File("/dev/null");
  @Option(name="learn.sgd.nu", gloss="The regularization $nu$ for SGD")
  public static double LEARN_SGD_NU = 0.1;
  @Option(name="learn.threads", gloss="The number of threads to run training on. This is primarily determined by the server capacity")
  public static int LEARN_THREADS = 4;

  @Option(name="script.wordnet.path", gloss="The path to the saved wordnet ontology (see sim.jar)")
  public static String SCRIPT_WORDNET_PATH = "etc/ontology_wordnet3.1.ser.gz";
  @Option(name="script.distsim.cos", gloss="The path to the cosine similarity nearest neighbors")
  public static String SCRIPT_DISTSIM_COS = "/home/gabor/workspace/truth/etc/cosNN.tab";

  @Option(name="script.reverb.raw.dir", gloss="The raw directory where Reverb outputs are stored")
  public static File SCRIPT_REVERB_RAW_DIR = new File("/scr/nlp/data/openie/output/reverb1.4/clueweb_english");
  @Option(name="script.reverb.raw.gzip", gloss="If true, the input files are in gzip format")
  public static boolean SCRIPT_REVERB_RAW_GZIP = true;
  @Option(name="script.reverb.head.do", gloss="If true, compute the head word for each fact added")
  public static boolean SCRIPT_REVERB_HEAD_DO = false;
  
  @Option(name="script.freebase.raw.path", gloss="The location of the raw Freebase dump")
  public static File SCRIPT_FREEBASE_RAW_PATH = new File("/u/nlp/data/semparse/rdf/scr/jackson/state/execs/93.exec/0.ttl");
  @Option(name="script.freebase.path", gloss="The location of the processed Freebase dump")
  public static File SCRIPT_FREEBASE_PATH = new File("etc/freebase.tab");

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
      } }, props);
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
