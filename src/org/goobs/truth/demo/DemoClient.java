package org.goobs.truth.demo;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A servlet for a frontend demo to illustrate the inference portion of the project.
 *
 * @author Gabor Angeli
 */
public class DemoClient extends HttpServlet {

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  public void doGet(HttpServletRequest request,
                    HttpServletResponse response) throws ServletException, IOException {
    response.setCharacterEncoding("utf8");
    response.setContentType("application/json");
    PrintWriter out = response.getWriter();
    out.write("{response: \"hello world\"");
    out.close();
  }

  @Override
  public void destroy() {
    super.destroy();
  }

}
