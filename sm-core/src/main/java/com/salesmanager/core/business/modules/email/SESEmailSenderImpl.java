package com.salesmanager.core.business.modules.email;

import java.io.StringWriter;
import jakarta.inject.Inject;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;
import com.salesmanager.core.business.modules.aws.AwsRegions;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

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

      SendEmailRequest request = SendEmailRequest.builder()
          .destination(Destination.builder().toAddresses(email.getTo()).build())
          .message(Message.builder()
              .body(Body.builder()
                  .html(utf8(prepareHtml(email)))
                  .text(utf8(TEXTBODY))
                  .build())
              .subject(utf8(email.getSubject()))
              .build())
          .source(email.getFromEmail())
          .build();

      try (SesClient client = SesClient.builder().region(AwsRegions.of(region, null)).build()) {
        client.sendEmail(request);
      }


  }

  private static Content utf8(String data) {
    return Content.builder().charset("UTF-8").data(data).build();
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
