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
import java.util.HashMap;
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

  public static enum Corpus { HELD_OUT, FRACAS, FRACAS_NATLOG, AVE_2006, AVE_2007, AVE_2008, MTURK_TRAIN, MTURK_TEST }

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
  @Option(name="natlog.indexer.replner", gloss="If true, replace unknown tokens with the NER tag they represent")
  public static boolean NATLOG_INDEXER_REPLNER = false;

  @Option(name="server.main.host", gloss="The hostname for the inference server")
  public static String SERVER_MAIN_HOST = "localhost";
  @Option(name="server.main.port", gloss="The hostname for the inference server")
  public static int SERVER_MAIN_PORT = 1337;
  @Option(name="server.backup.host", gloss="The hostname for the inference server")
  public static String SERVER_BACKUP_HOST = "localhost";
  @Option(name="server.backup.port", gloss="The hostname for the inference server")
  public static int SERVER_BACKUP_PORT = 1338;

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
  @Option(name="data.ave.path", gloss="The paths to the AVE answer validation examples by year")
  public static Map<String,File> DATA_AVE_PATH = new HashMap<String,File>() {{
    put("2006", new File("etc/ave/ave2006.tab"));
    put("2007", new File("etc/ave/ave2007.tab"));
    put("2008", new File("etc/ave/ave2008.tab"));
  }};
  @Option(name="data.mturk.train", gloss="The path to the MTurk annotated ReVerb training examples")
  public static File DATA_MTURK_TRAIN = new File("etc/mturk/reverb_train.tab");
  @Option(name="data.mturk.test", gloss="The path to the MTurk annotated Reverb test examples")
  public static File DATA_MTURK_TEST = new File("etc/mturk/reverb_test.tab");

  @Option(name="learn.iterations", gloss="The number of iterations to run learning for")
  public static int LEARN_ITERATIONS = 100;
  @Option(name="learn.model.dir", gloss="The directory to look for models in")
  public static File LEARN_MODEL_DIR = new File("/dev/null");
  @Option(name="learn.model.start", gloss="The start iteration to resume training from. This will be floored to the nearest 1k")
  public static int LEARN_MODEL_START = 0;
  @Option(name="learn.sgd.nu", gloss="The regularization $nu$ for SGD")
  public static double LEARN_SGD_NU = 0.1;
  @Option(name="learn.threads", gloss="The number of threads to run training on. This is primarily determined by the server capacity")
  public static int LEARN_THREADS = 4;
  @Option(name="learn.train", gloss="The corpus to use for training")
  public static Corpus[] LEARN_TRAIN = new Corpus[]{ Corpus.FRACAS };
  @Option(name="learn.test", gloss="The corpus to use for testing")
  public static Corpus[] LEARN_TEST = new Corpus[]{ Corpus.FRACAS };

  @Option(name="evaluate.allowunk", gloss="Allow unknown as a state we are evaluating against")
  public static boolean EVALUATE_ALLOWUNK = false;

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
