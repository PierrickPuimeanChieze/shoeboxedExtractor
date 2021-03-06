package be.cleitech.receipt.google;

import be.cleitech.receipt.MailManager;
import be.cleitech.receipt.MailProperties;
import be.cleitech.receipt.tasks.ProcessTaskResult;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring4.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ppc on 1/26/2017.
 */

public class GmailService implements MailManager {

    private static Log LOG = LogFactory.getLog(GmailService.class);
    private final Gmail gmail;
    private final MailProperties mailProperties;
    private SpringTemplateEngine thymeleafEngine;


    public GmailService(

            HttpTransport httpTransport,
            Credential credential,
            JsonFactory jsonFactory, String applicationName,
            SpringTemplateEngine thymeleafEngine, MailProperties mailProperties) throws GeneralSecurityException, IOException {
        this.thymeleafEngine = thymeleafEngine;

        gmail = new Gmail.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(applicationName)
                .build();
        this.mailProperties = mailProperties;
    }


    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    private static Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to       email address of the receiver
     * @param from     email address of the sender, the mailbox account
     * @param subject  subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    private static MimeMessage createEmail(String to,
                                           String from,
                                           String cc,
                                           String subject,
                                           String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(to));
        email.setSubject(subject);
        email.addRecipient(javax.mail.Message.RecipientType.CC,
                new InternetAddress(cc));
        email.setText(bodyText);
        email.setContent(bodyText, "text/html; charset=utf-8");
        return email;
    }

    public void sentExtractionResults(Collection<String> fileList) throws MessagingException {

        Context context = new Context();
        context.setVariable("fileList", fileList);
        Map<String, Object> model = new HashMap<>();
        model.put("fileList", fileList);
        String text = thymeleafEngine.process("uploadResult.mailTemplate", context);
        sentMail(text, mailProperties.getUploadResult());
    }

    @Override
    public void sentPublishOcrProcess(Collection<ProcessTaskResult> fileList) throws MessagingException {
        String text = "Publish Ocr - No File To upload";
        if (fileList != null) {
            Map<String, Object> model = new HashMap<>();
            Map<Boolean, List<ProcessTaskResult>> partitioningResults = fileList.stream().collect(Collectors.partitioningBy(ProcessTaskResult::isInError));
            model.put("results", partitioningResults.get(false));
            model.put("resultsInError", partitioningResults.get(true));
            Context context = new Context();
            context.setVariables(model);
            text = thymeleafEngine.process("publishOcr.mailTemplate", context);

        }
        MailProperties.MailInfo publishOcr = mailProperties.getPublishOcr();
        sentMail(text, publishOcr);
    }

    private void sentMail(String text, MailProperties.MailInfo publishOcr) throws MessagingException {
        LOG.debug("send Message with body " + text + " and info " + publishOcr);
        try {
            MimeMessage email = createEmail(
                    publishOcr.to,
                    publishOcr.from,
                    publishOcr.cc,
                    publishOcr.subject, text);
            Message message = createMessageWithEmail(email);
            Message sentMessage = gmail.users().messages().send("me", message).execute();
            LOG.debug("Message id: " + sentMessage.getId());
            LOG.debug("Message pretty String : " + sentMessage.toPrettyString());
        } catch (IOException e) {
            throw new MessagingException("mail error", e);
        }
    }

    @Override
    public void sendErrorMessage(String[] args, String errorContent) throws MessagingException {
        Map<String, Object> model = new HashMap<>();
        model.put("operationArgs", args);
        model.put("errorContent", errorContent);
        String text = "";
//        String text = VelocityEngineUtils.mergeTemplateIntoString(
//                velocityEngine, "errorMessage.mailTemplate.html", "UTF-8", model);
        sentMail(text, mailProperties.getErrorMessage());
    }
}
