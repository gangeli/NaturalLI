package org.goobs.naturalli.demo;

import com.google.gson.Gson;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import org.goobs.naturalli.*;
import org.goobs.naturalli.scripts.Truth;
import scala.Enumeration;
import scala.collection.Iterable;
import scala.collection.Iterator;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A servlet for a frontend demo to illustrate the inference portion of the project.
 *
 * @author Gabor Angeli
 */
public class NaturalLI extends HttpServlet {

  private static class JustificationElement {
    public String gloss;
    public String incomingRelation;
    public double cost;
  }

  private static class Response {
    public boolean success = false;
    public String errorMessage = "";

    public String bestResponseSource = "none";
    public boolean isTrue = false;
    public double score = -1.0;
    public JustificationElement[] bestJustification = new JustificationElement[0];
  }

  private static Counter<String> hardWeights = NatLog.hardNatlogWeights();
  private static Counter<String> softWeights = NatLog.softNatlogWeights();

  private ExecutorService pool = Executors.newFixedThreadPool(16);

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    Props.SERVER_MAIN_HOST = "localhost";
    Props.SERVER_MAIN_PORT = 1337;
    Props.SERVER_BACKUP_DO = false;
    Props.DEMO_DO = true;
  }

  private static String toString(Messages.Fact fact) {
    List<String> words = new ArrayList<>(fact.getWordCount());
    for (Messages.Word word : fact.getWordList()) {
      words.add(Utils.wordGloss()[word.getWord()]);
    }
    return StringUtils.join(words, " ");
  }

  private static JustificationElement mkJustificationElement(Messages.Inference inference) {
    JustificationElement elem = new JustificationElement();
    elem.gloss = toString(inference.getFact());
    if (inference.hasIncomingEdgeType()) {
      Enumeration.ValueSet values = EdgeType.values();
      Iterator<Enumeration.Value> iter = values.iterator();
      while (iter.hasNext()) {
        Enumeration.Value candidate = iter.next();
        if (inference.getIncomingEdgeType() == candidate.id()) {
          elem.incomingRelation = EdgeType.toMacCartneyRelation(candidate);
        }
      }
    } else {
      elem.incomingRelation = "  ()";
    }
    elem.cost = inference.getIncomingEdgeCost();
    return elem;
  }

  @Override
  public void doGet(HttpServletRequest request,
                    HttpServletResponse response) throws ServletException, IOException {
    Gson gson = new Gson();
    Response r = new Response();
    response.setCharacterEncoding("utf8");
    response.setContentType("application/json");
    PrintWriter out = response.getWriter();

    response.setHeader("Content-Type", "application/json");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Access-Control-Allow-Origin", "*");

    String input = request.getParameter("q");
    if (input == null || input.trim().equals("")) {
      r.success = false;
      r.errorMessage = "No input given";
    } else {
      final Lock callCompleteLock = new ReentrantLock();
      final Condition callComplete = callCompleteLock.newCondition();
      callCompleteLock.lock();
      try {
        pool.submit(() -> handleQuery(r, input, callCompleteLock, callComplete));
        callComplete.await();
      } catch (InterruptedException e) {
      } finally {
        callCompleteLock.unlock();
      }
    }

    out.print(gson.toJson(r));
    out.close();
  }

  @Override
  public void destroy() {
    super.destroy();
  }

  /** A recursive function to compute the justification entries */
  private LinkedList<JustificationElement> justification(Messages.Inference node) {
    if (node.hasImpliedFrom()) {
      if (node.hasIncomingEdgeType() && node.getIncomingEdgeType() != 63) {
        LinkedList<JustificationElement> recurse = justification(node.getImpliedFrom());
        recurse.addLast(mkJustificationElement(node));
        return recurse;
      } else {
        return justification(node.getImpliedFrom());
      }
    } else {
      LinkedList<JustificationElement> baseCase = new LinkedList<>();
      baseCase.addLast(mkJustificationElement(node));
      return baseCase;
    }
  }

  /**
   * Actually handle the query.
   */
  public void handleQuery(Response r, String input, Lock doneLockOrNull, Condition doneConditionOrNull) {
    // Register success -- can overwrite later
    r.success = true;

    // -- DO QUERY --
    Messages.Fact consequent = NatLog.annotate(input).head();
    Messages.Query query = Messages.Query.newBuilder()
        .setQueryFact(consequent)
        .setUseRealWorld(true)
        .setTimeout(100000)
        .setCosts(Learn.weightsToCosts(softWeights))
        .setSearchType("ucs")
        .setCacheType("bloom")
        .build();
    Iterable<Messages.Inference> paths = Truth.issueQuery(query, false, false);

    // -- PARSE QUERY --
    if (paths.headOption().isDefined()) {
      Messages.Inference bestPath = paths.head();
      // (parse truth)
      r.bestResponseSource = "Strict Natural Logic";
      switch(bestPath.getState()) {
        case TRUE:
          r.isTrue = true;
          break;
        case FALSE:
          r.isTrue = false;
          break;
        case UNKNOWN:
          r.bestResponseSource = "none";
      }
      // (parse score)
      r.score = bestPath.getScore();
      // (parse justification)
      LinkedList<JustificationElement> justification = justification(bestPath);
      r.bestJustification = justification.toArray(new JustificationElement[justification.size()]);
    } else {
      // Case: no paths returned!
      r.bestResponseSource = "none";
      r.isTrue = false;
      r.score = 999.9;
      r.bestJustification = new JustificationElement[0];
    }

    // Signal return
    if (doneLockOrNull != null && doneConditionOrNull != null) {
      doneLockOrNull.lock();
      try {
        doneConditionOrNull.signalAll();
      } finally {
        doneLockOrNull.unlock();
      }
    }
  }

}
