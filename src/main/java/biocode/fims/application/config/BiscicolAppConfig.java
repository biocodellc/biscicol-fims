package biocode.fims.application.config;

import biocode.fims.run.EzidUpdator;
import biocode.fims.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;

import java.io.FileNotFoundException;

/**
 * Configuration class for Biscicol-Fims applications. Including cli and webapps
 */
@Configuration
@Import({FimsAppConfig.class})
// declaring this here allows us to override any properties that are also included in biscicol-fims.props
@PropertySource(value = "classpath:biocode-fims.props", ignoreResourceNotFound = true)
@PropertySource("classpath:biscicol-fims.props")
public class BiscicolAppConfig {
    @Autowired
    FimsAppConfig fimsAppConfig;
    @Autowired
    ProjectService projectService;

    @Bean
    public EzidUpdator ezidUpdator() throws FileNotFoundException {
        return new EzidUpdator(fimsAppConfig.bcidService, fimsAppConfig.settingsManager);
    }
}
