package org.goobs.naturalli.demo;

import javax.mail.*;
import javax.mail.internet.*;

import java.util.Properties;
import java.util.Date;

/**
 * A simple interface to the javamail library.  You can use this to
 * send yourself mail when a program succeeds, fails, etc.
 * <br>
 * Adapted from the "msgsendsample.java" example included with the
 * Oracle javamail implementation.
 *
 * @author John Bauer
 * @author Gabor Angeli (remove "smtp server" options, defaulting to a Gmail server; add more utility methods)
 */
@SuppressWarnings("UnusedDeclaration")
public class SendMail {
  private SendMail() {} // static member functions only

  /** Send a plaintext email to a given address */
  public static void sendTextMail(String to, String subject, String body) {
    sendMail("stanfordnlpmailer@gmail.com", to, subject, body, false);
  }

  /** Send a plaintext email to a given address */
  public static void sendTextMail(String from, String to, String subject, String body) {
    sendMail(from, to, subject, body, false);
  }

  /** Send an HTML formatted email to a given address */
  public static void sendHTMLMail(String to, String subject, String body) {
    sendMail("stanfordnlpmailer@gmail.com", to, subject, body, true);
  }

  /** Send an HTML formatted email to a given address */
  public static void sendHTMLMail(String from, String to, String subject, String body) {
    sendMail(from, to, subject, body, true);
  }

  /** Send an email
   *
   * @param to The address to send the email to
   * @param subject The subject of the email
   * @param body The body of the email, either as HTML or as plaintext
   * @param html A flag for whether the body of the email should be interpreted as HTML
   */
  public static void sendMail(String from, String to, String subject, String body, boolean html) {
    // create some properties and get the default Session
    Properties props = new Properties();
    props.put("mail.transport.protocol", "smtps");
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.ssl.enable", "true");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.socketFactory.port", 465);
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    props.put("mail.smtp.socketFactory.fallback", "false");
    props.put("mail.debug", "false");
    Authenticator auth = new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("stanfordnlpmailer@gmail.com", "asecurephrase");
      }
    };

    Session session = Session.getInstance(props, auth);

    try {
      // create a message
      MimeMessage msg = new MimeMessage(session);
      msg.setFrom(new InternetAddress(from));
      InternetAddress[] address = {new InternetAddress(to)};
      msg.setRecipients(Message.RecipientType.TO, address);
      msg.setSubject(subject);
      msg.setSentDate(new Date());
      // If the desired charset is known, you can use
      // setText(text, charset)
      if (html) {
        msg.setContent(body, "text/html; charset=utf-8");
      } else {
        msg.setText(body);
      }

      Transport.send(msg);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}