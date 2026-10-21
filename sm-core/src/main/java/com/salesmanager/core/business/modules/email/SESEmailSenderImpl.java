package com.salesmanager.core.business.modules.email;

import java.io.StringWriter;
import java.util.Locale;
import javax.inject.Inject;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
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
  private volatile SesV2Client sesV2Client;
  private volatile String sesRegion;

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

      SesV2Client client = sesV2Client();
      SendEmailRequest request = SendEmailRequest.builder()
          .destination(Destination.builder().toAddresses(email.getTo()).build())
          .content(EmailContent.builder().simple(Message.builder()
              .body(Body.builder()
                  .html(Content.builder().charset("UTF-8").data(prepareHtml(email)).build())
                  .text(Content.builder().charset("UTF-8").data(TEXTBODY).build())
                  .build())
              .subject(Content.builder().charset("UTF-8").data(email.getSubject()).build())
              .build()).build())
          .fromEmailAddress(email.getFromEmail()).build();
          // Comment or remove the next line if you are not using a
          // configuration set
          //.withConfigurationSetName(CONFIGSET);
      client.sendEmail(request);


  }

  protected SesV2Client sesV2Client() {
    Validate.notNull(region, "AWS region is null");
    String normalizedRegion = region.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    if (sesV2Client == null || !normalizedRegion.equals(sesRegion)) {
      synchronized (this) {
        if (sesV2Client == null || !normalizedRegion.equals(sesRegion)) {
          sesV2Client = SesV2Client.builder().region(Region.of(normalizedRegion))
              .credentialsProvider(DefaultCredentialsProvider.create()).build();
          sesRegion = normalizedRegion;
        }
      }
    }
    return sesV2Client;
  }

  void setSesV2Client(SesV2Client sesV2Client) {
    this.sesV2Client = sesV2Client;
    this.sesRegion = region == null ? null
        : region.trim().replace('_', '-').toLowerCase(Locale.ROOT);
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
