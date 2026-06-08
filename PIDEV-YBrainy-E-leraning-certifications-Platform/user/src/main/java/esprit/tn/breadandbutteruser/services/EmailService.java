package esprit.tn.breadandbutteruser.services;

import org.springframework.core.io.Resource;

import java.util.Map;

public interface EmailService {
    void sendHtmlEmail(String to, String subject, String htmlBody, Map<String, Resource> inlineResources);
}
