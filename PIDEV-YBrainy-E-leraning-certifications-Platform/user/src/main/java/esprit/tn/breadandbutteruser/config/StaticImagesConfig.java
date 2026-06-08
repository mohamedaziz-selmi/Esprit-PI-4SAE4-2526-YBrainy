package esprit.tn.breadandbutteruser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Maps the public /images/** URL space to the on-disk avatar directory
 * configured via app.upload.images-dir. Spring Boot's default static
 * mapping covers classpath:/static, not external dirs, so we wire it
 * explicitly.
 */
@Configuration
public class StaticImagesConfig implements WebMvcConfigurer {

    @Value("${app.upload.images-dir:/app/uploads/images}")
    private String imagesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imagesDir.endsWith("/") ? imagesDir : imagesDir + "/";
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + location);
    }
}
