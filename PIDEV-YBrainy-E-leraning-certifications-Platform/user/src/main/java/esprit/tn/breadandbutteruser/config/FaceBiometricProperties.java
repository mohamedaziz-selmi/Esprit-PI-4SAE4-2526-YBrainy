package esprit.tn.breadandbutteruser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.face-biometric")
public class FaceBiometricProperties {
    private String pythonCommand = "py";
    private String scriptPath = "scripts/face_biometric.py";
    private String cascadePath = "src/main/resources/biometrics/haarcascade_frontalface_alt.xml";
    private String storageDir = "tmp/face-biometric";
}
