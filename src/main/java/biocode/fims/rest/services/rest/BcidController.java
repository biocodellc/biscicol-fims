package biocode.fims.rest.services.rest;

import biocode.fims.authorizers.ProjectAuthorizer;
import biocode.fims.application.config.FimsProperties;
import biocode.fims.service.BcidService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import javax.ws.rs.Path;

/**
 * @author RJ Ewing
 */
@Controller
@Path("bcids")
public class BcidController extends FimsAbstractBcidController {
    @Autowired
    BcidController(BcidService bcidService, FimsProperties props, ProjectAuthorizer projectAuthorizer) {
        super(bcidService, props, projectAuthorizer);
    }
}
