package org.goobs.naturalli.demo;

import org.goobs.naturalli.Props;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

/**
 * A servlet for providing feedback to NaturalLI about whether it got a query right or wrong.
 *
 * @author Gabor Angeli
 */
public class Feedback extends HttpServlet {

  private Connection psql;
  private PreparedStatement insert;
  private PreparedStatement delete;

  @Override
  public void init(ServletConfig config) throws ServletException {
    String uri = "jdbc:postgresql://" + Props.PSQL_HOST + ":" + Props.PSQL_PORT + "/" + Props.PSQL_DB;
    try {
      Class.forName("org.postgresql.Driver");
      psql = DriverManager.getConnection(uri, Props.PSQL_USERNAME, Props.PSQL_PASSWORD);
      insert = psql.prepareStatement("INSERT INTO demo_feedback (id, source, query, guess, gold) VALUES (?, ?, ?, ?, ?);");
      delete = psql.prepareStatement("DELETE FROM demo_feedback WHERE id=?;");
    } catch (SQLException | ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("SynchronizeOnNonFinalField")
  @Override
  public void doGet(HttpServletRequest request,
                    HttpServletResponse response) throws ServletException, IOException {
    // Prepare response
    response.setCharacterEncoding("utf8");
    response.setContentType("application/text");
    PrintWriter out = response.getWriter();
    response.setHeader("Content-Type", "application/json");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Access-Control-Allow-Origin", "*");

    // Parse arguments
    boolean isCancel = parseBool(request, "isCancel");
    boolean guess = parseBool(request, "guess");
    boolean gold = parseBool(request, "gold");
    String transactionId = request.getParameter("transactionID");
    if (transactionId == null) {
      out.println("no transactionID given!");
      out.close();
      return;
    }
    String query = request.getParameter("q");
    if (query == null) {
      out.println("no query (q=???) given!");
      out.close();
      return;
    }
    String source = request.getParameter("source");
    if (source == null) { source = "?"; }

    // Save
    try {
      if (psql == null || insert == null) {
        out.println("no connection to the database");
        out.close();
        return;
      }

      // -- BEGIN CONTENT
      if (isCancel) {
        synchronized (delete) {
          delete.setString(1, transactionId);
          out.print("Delete result: " + delete.execute());
        }
      } else {
        synchronized (insert) {
          insert.setString(1, transactionId);
          insert.setString(2, source);
          insert.setString(3, query);
          insert.setBoolean(4, guess);
          insert.setBoolean(5, gold);
          out.print("Insert result: " + insert.execute());
        }
      }
      // -- END CONTENT

    } catch (RuntimeException | SQLException e) {
      out.println(e.getClass());
      out.println(e.getMessage());
      out.println("@   " + e.getStackTrace()[0]);
      e.printStackTrace();
    }

    // Send response
    out.close();
  }

  private static boolean parseBool(HttpServletRequest request, String key) {
    String boolAsString = request.getParameter(key);
    if (boolAsString == null || boolAsString.trim().equals("")) {
      return false;
    }
    try {
      return Boolean.parseBoolean(boolAsString);
    } catch (RuntimeException e) {
      try {
        return Integer.parseInt(boolAsString) != 0;
      } catch (RuntimeException e2) {
        return boolAsString.equalsIgnoreCase("t");
      }
    }
  }
}
