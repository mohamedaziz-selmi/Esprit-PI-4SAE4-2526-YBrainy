package esprit.tn.breadandbutteruser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import esprit.tn.breadandbutteruser.config.FaceBiometricProperties;
import esprit.tn.breadandbutteruser.config.KeycloakAdminProperties;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties({KeycloakAdminProperties.class, FaceBiometricProperties.class})
public class BreadandbutteruserApplication {

    public static void main(String[] args) {
        SpringApplication.run(BreadandbutteruserApplication.class, args);
    }

}
