package com.salesmanager.core.business.modules.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import freemarker.template.Configuration;

public class DefaultEmailSenderImplTest {

  private SmtpStub platformSmtp;
  private SmtpStub storeSmtp;
  private JavaMailSenderImpl sharedMailSender;
  private DefaultEmailSenderImpl sender;

  @Before
  public void setUp() throws IOException {
    platformSmtp = new SmtpStub();
    storeSmtp = new SmtpStub();

    sharedMailSender = new JavaMailSenderImpl();
    sharedMailSender.setProtocol("smtp");
    sharedMailSender.setHost("127.0.0.1");
    sharedMailSender.setPort(platformSmtp.port());

    sender = new DefaultEmailSenderImpl();
    sender.setMailSender(sharedMailSender);
    sender.setFreemarkerMailConfiguration(new Configuration(Configuration.VERSION_2_3_23));
  }

  @After
  public void tearDown() throws IOException {
    platformSmtp.close();
    storeSmtp.close();
  }

  @Test
  public void storeConfigDoesNotLeakIntoSharedSender() throws Exception {
    EmailConfig storeConfig = new EmailConfig();
    storeConfig.setProtocol("smtp");
    storeConfig.setHost("127.0.0.1");
    storeConfig.setPort(String.valueOf(storeSmtp.port()));
    storeConfig.setUsername("mallory");
    storeConfig.setPassword("secret");

    sender.send(email("customer@store-a.test"), storeConfig);
    sender.send(email("admin@shopizer.com"), null);

    assertEquals(1, storeSmtp.recipients().size());
    assertTrue(storeSmtp.recipients().get(0).contains("customer@store-a.test"));
    assertEquals(1, platformSmtp.recipients().size());
    assertTrue(platformSmtp.recipients().get(0).contains("admin@shopizer.com"));

    assertEquals("127.0.0.1", sharedMailSender.getHost());
    assertEquals(platformSmtp.port(), sharedMailSender.getPort());
    assertEquals(null, sharedMailSender.getUsername());
    assertEquals(null, sharedMailSender.getPassword());
  }

  @Test
  public void sendWithoutConfigUsesSharedSender() throws Exception {
    sender.send(email("someone@example.test"));

    assertEquals(1, platformSmtp.recipients().size());
    assertEquals(0, storeSmtp.recipients().size());
  }

  @Test
  public void rejectsStoreConfigWithoutHostOrValidPort() {
    assertRejected(config(null, "25"));
    assertRejected(config(" ", "25"));
    assertRejected(config("127.0.0.1", null));
    assertRejected(config("127.0.0.1", "abc"));
    assertRejected(config("127.0.0.1", "0"));
    assertRejected(config("127.0.0.1", "65536"));
    assertEquals(0, platformSmtp.recipients().size());
  }

  private void assertRejected(EmailConfig config) {
    try {
      sender.send(email("someone@example.test"), config);
      fail("expected config to be rejected: " + config.getHost() + ":" + config.getPort());
    } catch (IllegalArgumentException expected) {
      // expected
    } catch (Exception e) {
      fail("unexpected exception " + e);
    }
  }

  private static EmailConfig config(String host, String port) {
    EmailConfig config = new EmailConfig();
    config.setHost(host);
    config.setPort(port);
    return config;
  }

  private static Email email(String to) {
    Email email = new Email();
    email.setFrom("Shopizer");
    email.setFromEmail("noreply@shopizer.test");
    email.setTo(to);
    email.setSubject("Test");
    email.setTemplateName("email_template_test.ftl");
    email.setTemplateTokens(new HashMap<>());
    return email;
  }

  /** Minimal single-threaded SMTP server that records RCPT TO lines. */
  private static final class SmtpStub implements AutoCloseable {
    private final ServerSocket server;
    private final List<String> recipients = new CopyOnWriteArrayList<>();
    private final Thread thread;

    SmtpStub() throws IOException {
      server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
      thread = new Thread(this::serve, "smtp-stub-" + server.getLocalPort());
      thread.setDaemon(true);
      thread.start();
    }

    int port() {
      return server.getLocalPort();
    }

    List<String> recipients() {
      return recipients;
    }

    private void serve() {
      while (!server.isClosed()) {
        try (Socket socket = server.accept();
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {
          reply(out, "220 stub");
          String line;
          boolean data = false;
          while ((line = in.readLine()) != null) {
            if (data) {
              if (line.equals(".")) {
                data = false;
                reply(out, "250 queued");
              }
              continue;
            }
            String upper = line.toUpperCase();
            if (upper.startsWith("RCPT TO")) {
              recipients.add(line);
              reply(out, "250 ok");
            } else if (upper.startsWith("DATA")) {
              data = true;
              reply(out, "354 go");
            } else if (upper.startsWith("QUIT")) {
              reply(out, "221 bye");
              break;
            } else {
              reply(out, "250 ok");
            }
          }
        } catch (IOException e) {
          // socket closed
        }
      }
    }

    private static void reply(PrintWriter out, String line) {
      out.print(line + "\r\n");
      out.flush();
    }

    @Override
    public void close() throws IOException {
      server.close();
    }
  }
}
