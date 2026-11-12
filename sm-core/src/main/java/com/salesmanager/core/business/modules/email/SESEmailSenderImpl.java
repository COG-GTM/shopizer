package com.salesmanager.core.business.modules.email;

import java.io.StringWriter;
import jakarta.inject.Inject;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * AWS HTML email sender
 * 
 * @author carlsamson
 *
 */
@Component("sesEmailSender")
public class SESEmailSenderImpl implements EmailModule {

  @Inject
  private Configuration freemarkerMailConfiguration;
  
  @Value("${config.emailSender.region}")
  private String region;

  private SesClient client;

  void setClient(SesClient client) {
    this.client = client;
  }

  private final static String TEMPLATE_PATH = "templates/email";

  // The configuration set to use for this email. If you do not want to use a
  // configuration set, comment the following variable and the
  // .withConfigurationSetName(CONFIGSET); argument below.
  //static final String CONFIGSET = "ConfigSet";


  // The email body for recipients with non-HTML email clients.
  static final String TEXTBODY =
      "This email was sent through Amazon SES " + "using the AWS SDK for Java.";

  @Override
  public void send(Email email) throws Exception {



      //String eml = email.getFrom();

      Validate.notNull(region,"AWS region is null");

      if (client == null) {
        client = SesClient.builder()
            .region(Region.of(region.toLowerCase(java.util.Locale.ROOT).replace('_', '-')))
            .credentialsProvider(DefaultCredentialsProvider.create()).build();
      }
      SendEmailRequest request = SendEmailRequest.builder()
          .destination(Destination.builder().toAddresses(email.getTo()).build())
          .message(Message.builder()
              .body(Body.builder().html(Content.builder().charset("UTF-8").data(prepareHtml(email)).build())
                  .text(Content.builder().charset("UTF-8").data(TEXTBODY).build()).build())
              .subject(Content.builder().charset("UTF-8").data(email.getSubject()).build()).build())
          .source(email.getFromEmail()).build();
          // Comment or remove the next line if you are not using a
          // configuration set
          //.withConfigurationSetName(CONFIGSET);
      client.sendEmail(request);


  }

  private String prepareHtml(Email email) throws Exception {


    freemarkerMailConfiguration.setClassForTemplateLoading(DefaultEmailSenderImpl.class, "/");
    Template htmlTemplate = freemarkerMailConfiguration.getTemplate(new StringBuilder(TEMPLATE_PATH)
            .append("/").append(email.getTemplateName()).toString());
    final StringWriter htmlWriter = new StringWriter();
    try {
      htmlTemplate.process(email.getTemplateTokens(), htmlWriter);
    } catch (TemplateException e) {
      throw new MailPreparationException("Can't generate HTML mail", e);
    }

    return htmlWriter.toString();

  }

  @Override
  public void setEmailConfig(EmailConfig emailConfig) {
    // TODO Auto-generated method stub

  }

}
