package org.goobs.naturalli.demo;

import edu.stanford.nlp.time.SUTime;
import org.goobs.naturalli.Messages;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * A tiny servlet to shut down the running server.
 *
 * @author Gabor Angeli
 */
public class Shutdown extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest request,
                    HttpServletResponse response) throws ServletException, IOException {
    PrintWriter out = response.getWriter();
    String host = request.getParameter("host");
    String port = request.getParameter("port");
    String time = request.getParameter("time");

    if (host == null || port == null || time == null) {
      out.println("Usage: shutdown?host=<hostname>&port=<port>&time=<yyyy-mm-dd>");
      out.close();
    } else {
      try {
        int portNum = Integer.parseInt(port);

        try {
          // Check time
          long checksumTime = SUTime.parseDateTime(time).getJodaTimeInstant().getMillis();
          long currentTime = SUTime.getCurrentTime().getJodaTimeInstant().getMillis();
          if (Math.abs(currentTime - checksumTime) <= 1000l * 60 * 60 * 24) {
            // Do shutdown
            Messages.Query query = Messages.Query.newBuilder().setQueryFact(Messages.Fact.newBuilder().addWord(Messages.Word.newBuilder().setWord(0).build()).build()).setUseRealWorld(false).setShutdownServer(true).build();
            try {
              // Set up connection
              Socket sock = new Socket(host, portNum);
              DataOutputStream toServer = new DataOutputStream(sock.getOutputStream());
              // Write query
              query.writeTo(toServer);
              sock.shutdownOutput();  // signal end of transmission
            } catch (RuntimeException e) {
              out.println(e.getClass() + " " + e.getMessage());
              out.println(e.getStackTrace()[0]);
              out.close();
            }
          } else {
            out.println("The time entered is too far away from now; please set time=YYYY-MM-DD in the query.");
            out.println("Current time: " + currentTime + " (" + SUTime.getCurrentTime().getJodaTimeInstant() + ")");
            out.println("Checksum time: " + checksumTime + " (" + SUTime.parseDateTime(time).getJodaTimeInstant() + ")");
          }
        } catch (RuntimeException e) {
          out.println(e.getClass() + " " + e.getMessage());
          out.println(e.getStackTrace()[0]);
          out.close();

        }
      } catch (NumberFormatException e) {
        out.println("port must be a valid integer");
        out.close();
      }
    }
  }
}
