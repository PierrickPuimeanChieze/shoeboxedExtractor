package be.cleitech.shoeboxed.extractor;

import org.apache.velocity.app.VelocityEngine;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.ui.velocity.VelocityEngineUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MailManagerImpl implements MailManager {

    private final VelocityEngine velocityEngine;
    private MailSender mailSender;
    private SimpleMailMessage uploadResultTemplateMessage;

    public MailManagerImpl(MailSender mailSender, VelocityEngine velocityEngine, SimpleMailMessage uploadResultTemplateMessage ) {
        this.mailSender = mailSender;
        this.velocityEngine = velocityEngine;
        this.uploadResultTemplateMessage = uploadResultTemplateMessage;
    }

    @Override
    public void sentExtractionResults(Collection<String> fileList) {
        Map<String, Object> model = new HashMap<>();
        model.put("fileList", fileList);
        String text = VelocityEngineUtils.mergeTemplateIntoString(
                velocityEngine, "velocity/uploadResult.mailTemplate.vm", "UTF-8", model);


        // Create a thread safe "copy" of the template message and customize it
        SimpleMailMessage msg = new SimpleMailMessage(uploadResultTemplateMessage);
        msg.setText(text);
        this.mailSender.send(msg);
    }

}