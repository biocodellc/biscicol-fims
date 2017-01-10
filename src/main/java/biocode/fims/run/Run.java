package biocode.fims.run;

import biocode.fims.application.config.BiscicolAppConfig;
import biocode.fims.config.ConfigurationFileFetcher;
import biocode.fims.digester.Mapping;
import biocode.fims.entities.User;
import biocode.fims.fileManagers.fimsMetadata.FimsMetadataFileManager;
import biocode.fims.fimsExceptions.FimsRuntimeException;
import biocode.fims.fimsExceptions.errorCodes.UploadCode;
import biocode.fims.fuseki.query.FimsQueryBuilder;
import biocode.fims.fuseki.triplify.Triplifier;
import biocode.fims.service.ExpeditionService;
import biocode.fims.service.UserService;
import biocode.fims.settings.FimsPrinter;
import biocode.fims.settings.SettingsManager;
import biocode.fims.settings.StandardPrinter;
import com.hp.hpl.jena.util.FileUtils;
import org.apache.commons.cli.*;
import org.json.simple.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to upload and triplify via cmd line
 */
public class Run {

    private final boolean ignoreUser;
    private final ExpeditionService expeditionService;
    private final FimsMetadataFileManager FimsMetadataFileManager;
    private ProcessController processController;

    public Run(ExpeditionService expeditionService, FimsMetadataFileManager FimsMetadataFileManager,
               ProcessController processController, boolean ignoreUser) {
        this.expeditionService = expeditionService;
        this.FimsMetadataFileManager = FimsMetadataFileManager;
        this.processController = processController;
        this.ignoreUser = ignoreUser;
    }

    /**
     * runAll method is designed to go through the FIMS process for a local application.  The REST services
     * would handle user input/output differently
     *
     * @param upload
     * @param deepRoots -- only set to FALSE for testing and debugging usually, or local triplify usage.
     */
    public void runAllLocally(Boolean upload, boolean deepRoots, Boolean forceAll, String writeTriplesLang) {
        if (!processController.getProcess().validate()) {
            // If there is errors, tell the user and stop the operation
            FimsPrinter.out.println(processController.printMessages());
            return;
        }

        if (processController.getHasWarnings()) {
            Boolean continueOperation;
            if (forceAll) {
                continueOperation = true;
            } else {
//                String message = "\tWarnings found on " + processController.getMapping().getDefaultSheetName() + " worksheet.\n" + processController.getCommandLineSB().toString();
                // In LOCAL version convert HTML tags to readable Text
                // the FimsInputter isn't working correctly, just using the StandardInputter for now
                //Boolean continueOperation = FimsInputter.in.continueOperation(message);
                continueOperation = new StandardInputter().continueOperation(processController.printMessages());
            }
            if (!continueOperation) {
                return;
            }
        }

        if (upload) {
            // We only need to check on assigning Expedition if the user wants to upload data
            processController.setExpeditionTitle(processController.getExpeditionCode() + " spreadsheet");
            try {
                boolean createExpedition = forceAll;
                processController.getProcess().upload(createExpedition, ignoreUser, expeditionService);
            } catch (FimsRuntimeException e) {
                if (e.getErrorCode() == UploadCode.EXPEDITION_CREATE) {
                    String message = "\nThe dataset code \"" + processController.getExpeditionCode() + "\" does not exist.  " +
                            "Do you wish to create it now?" +
                            "\nIf you choose to continue, your data will be associated with this new dataset code.";
                    Boolean continueOperation = FimsInputter.in.continueOperation(message);

                    if (!continueOperation)
                        return;
                    else {
                        processController.getProcess().upload(true, ignoreUser, expeditionService);
                    }
                } else {
                    throw e;
                }
            }
            FimsPrinter.out.println(processController.getSuccessMessage());

            // If we don't Run the expedition check then we DO NOT assign any ARK roots or special expedition information
            // In other, words, this is typically used for local debug & test modes
        } else {
            if (deepRoots) {
                runTriplifier(true, writeTriplesLang);
            } else {
                runTriplifier(false, writeTriplesLang);
            }
        }
    }

    private String runTriplifier(boolean entityRoots, String writeTriplesLang) {
        FimsPrinter.out.println("\nTriplifying...");

        // Run the triplifier
        Triplifier t = new Triplifier("test", processController.getOutputFolder(), processController);
        t.setOutputLanguage(writeTriplesLang);

        if (entityRoots) {
            expeditionService.setEntityIdentifiers(
                    processController.getMapping(),
                    processController.getExpeditionCode(),
                    processController.getProjectId()
            );

        }
        JSONObject resource = (JSONObject) FimsMetadataFileManager.getDataset().get(0);
        t.run(processController.getValidation().getSqliteFile(), new ArrayList<String>(resource.keySet()));
        FimsPrinter.out.println("\ttriple output file = " + t.getTripleOutputFile());
        return t.getTripleOutputFile();
    }

    /**
     * Run the program from the command-line
     *
     * @param args
     */
    public static void main(String args[]) throws Exception {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(BiscicolAppConfig.class);
        UserService userService = applicationContext.getBean(UserService.class);
        FimsMetadataFileManager FimsMetadataFileManager = applicationContext.getBean(FimsMetadataFileManager.class);
        SettingsManager settingsManager = applicationContext.getBean(SettingsManager.class);
        ExpeditionService expeditionService = applicationContext.getBean(ExpeditionService.class);

        String defaultOutputDirectory = System.getProperty("user.dir") + File.separator + "tripleOutput";
        String username = "";
        String password = "";
        int projectId = 0;
        //System.out.print(defaultOutputDirectory);

        // TEST configuration :
        // -d -t -u -i sampledata/Apogon***.xls

        // Direct output using the standardPrinter subClass of fimsPrinter which send to fimsPrinter.out (for command-line usage)
        FimsPrinter.out = new StandardPrinter();


        // Some classes to help us
        CommandLineParser clp = new GnuParser();
        HelpFormatter helpf = new HelpFormatter();
        CommandLine cl;

        // The expedition code corresponds to a expedition recognized by BCID
        String dataset_code = "";
        // The configuration template
        //String configuration = "";
        // The input file
        String input_file = "";
        // The directory that we write all our files to
        String output_directory = "tripleOutput";
        // Write spreadsheet content back to a spreadsheet file, for testing
        Boolean triplify = false;
        Boolean upload = false;
        Boolean local = false;
        Boolean force = false;


        // Define our commandline options
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message and exit");
        options.addOption("q", "query", true, "Run a query and pass in graph UUIDs to look at for this query -- Use this along with options C and S");
        options.addOption("f", "format", true, "excel|tab|csv|kml|json|cspace  specifying the return format for the query");
        options.addOption("F", "filter", true, "Filter results based on a keyword search");

        options.addOption("e", "dataset_code", true, "Dataset code.  You will need to obtain a data code before " +
                "loading data");
        options.addOption("o", "output_directory", true, "Output Directory");
        options.addOption("i", "input_file", true, "Input Spreadsheet or FASTA file");
        options.addOption("p", "project_id", true, "Project Identifier.  A numeric integer corresponding to your project");
        options.addOption("configFile", true, "Use a local config file instead of getting from server");

        options.addOption("t", "triplify", false, "Triplify only (upload process triplifies)");
        options.addOption("T", "writeTriplesFormat", true, "NTriple|Turtle|N3 Default is NTriple if not specified");

        options.addOption("l", "local", false, "Local option operates purely locally and does not create proper globally unique identifiers.  Running the local option means you don't need a username and password.");

        options.addOption("u", "upload", false, "Upload");

        options.addOption("U", "username", true, "Username (for uploading data)");
        options.addOption("P", "password", true, "Password (for uploading data)");
        options.addOption("y", "yes", false, "Answer 'y' to all questions");


        // Create the commands parser and parse the command line arguments.
        try {
            cl = clp.parse(options, args);
        } catch (UnrecognizedOptionException e) {
            FimsPrinter.out.println("Error: " + e.getMessage());
            return;
        } catch (ParseException e) {
            FimsPrinter.out.println("Error: " + e.getMessage());
            return;
        }

//         Set the input format
        if (cl.hasOption("y")) {
            FimsInputter.in = new ForceInputter();
            force = true;
        } else {
            FimsInputter.in = new StandardInputter();
        }

        // Set username
        if (cl.hasOption("U")) {
            username = cl.getOptionValue("U");
        }

        // Set password
        if (cl.hasOption("P")) {
            password = cl.getOptionValue("P");
        }

        // Check username and password
        if (cl.hasOption("u") && (username.equals("") || password.equals(""))) {
            FimsPrinter.out.println("Must specify a valid username or password for uploading data!");
            return;
        }

        // Query option must also have project_id option
        if (cl.hasOption("q")) {
            if (!cl.hasOption("p")) {
                helpf.printHelp("fims ", options, true);
                return;
            }

        }
        // Help
        else if (cl.hasOption("h")) {
            helpf.printHelp("fims ", options, true);
            return;
        }

        // No options returns help message
        if (cl.getOptions().length < 1) {
            helpf.printHelp("fims ", options, true);
            return;
        }

        // Sanitize project specification
        if (cl.hasOption("p")) {
            try {
                projectId = Integer.parseInt(cl.getOptionValue("p"));
            } catch (Exception e) {
                FimsPrinter.out.println("Bad option for project_id");
                helpf.printHelp("fims ", options, true);
                return;
            }
        }

        // Check for project_id when uploading data
        if (cl.hasOption("u") && projectId == 0) {
            FimsPrinter.out.println("Must specify a valid project_id when uploading data");
            return;
        }

        // Set input file
        if (cl.hasOption("i"))
            input_file = cl.getOptionValue("i");

        // Set output directory
        if (cl.hasOption("o"))
            output_directory = cl.getOptionValue("o");

        // Set dataset_code
        if (cl.hasOption("e"))
            dataset_code = cl.getOptionValue("e");

        // Set triplify option
        if (cl.hasOption("t"))
            triplify = true;

        // Set the "local" option
        if (cl.hasOption("l"))
            local = true;

        // Set upload option
        if (cl.hasOption("u"))
            upload = true;

        // Set default output directory if one is not specified
        if (!cl.hasOption("o")) {
            FimsPrinter.out.println("Using default output directory " + defaultOutputDirectory);
            output_directory = defaultOutputDirectory;
        }

        // Set the default writeTriplesLang option value
        String writeTriplesLang = FileUtils.langNTriple;

        if (cl.hasOption("writeTriplesFormat")) {
            if (cl.getOptionValue("writeTriplesFormat").toString().equalsIgnoreCase("Turtle")) {
                writeTriplesLang = FileUtils.langTurtle;
            }
            else if (cl.getOptionValue("writeTriplesFormat").toString().equalsIgnoreCase("N3")) {
                writeTriplesLang = FileUtils.langN3;
            } else {
                writeTriplesLang = FileUtils.langNTriple;
                System.out.println ("writeTriplesFormat option " + cl.getOptionValue("T") + " not recognized, defaulting to " + FileUtils.langNTriple);
            }
        }

        // Check that output directory is writable
        try {
            if (!new File(output_directory).canWrite()) {
                FimsPrinter.out.println("Unable to write to output directory " + output_directory);
                return;
            }
        } catch (Exception e) {
            FimsPrinter.out.println("Unable to write to output directory " + output_directory);
            return;
        }

        // Run the command
        try {
            /*
            Run a query
             */
            if (cl.hasOption("q")) {

                File configFile = new ConfigurationFileFetcher(projectId, output_directory, true).getOutputFile();

                Mapping mapping = new Mapping();
                mapping.addMappingRules(configFile);

                //p.query(cl.getOptionValue("q"), cl.getOptionValue("f"), cl.getOptionValue("F"));
                // TODO: construct filter statements from arguments passed in on command-line
                // Build the Query Object by passing this object and an array of graph objects, separated by commas
                FimsQueryBuilder q = new FimsQueryBuilder(mapping, cl.getOptionValue("q").split(","), output_directory);
                // Run the query, passing in a format and returning the location of the output file
                switch (cl.getOptionValue("f")) {
                    case "json":
                        System.out.println(q.getJSON());
                        break;
                    case "excel":
                        System.out.println(q.writeExcel(projectId));
                        break;
                    case "tab":
                        System.out.println(q.writeTAB());
                        break;
                    case "csv":
                        System.out.println(q.writeCSV());
                        break;
                    case "kml":
                        System.out.println(q.writeKML());
                        break;
                }
            }
            /*
           Run the validator
            */
            else {
                ProcessController processController = new ProcessController(projectId, dataset_code);
                processController.setOutputFolder(output_directory);

                boolean ignoreUser = Boolean.parseBoolean(settingsManager.retrieveValue("ignoreUser"));
                Run run = new Run(expeditionService, FimsMetadataFileManager, processController, ignoreUser);
                Map<String, Map<String, Object>> fmProps = new HashMap<>();
                Map<String, Object> props = new HashMap<>();
                props.put("filename", input_file);

                fmProps.put("fimsMetadata", props);

                // if we only want to triplify and not upload, then we operate in LOCAL mode
                if (local && triplify) {
                    processController.appendStatus("Triplifying using LOCAL only options, useful for debugging\n");
                    processController.appendStatus("Does not construct GUIDs, use Deep Roots, or connect to project-specific configurationFiles");

                    // Create the process object --- this is done each time to orient the application
                    Process process = new Process.ProcessBuilder(FimsMetadataFileManager, processController)
                            .addFmProperties(fmProps)
                            .configFile(new File(cl.getOptionValue("configFile")))
                            .build();

                    processController.setProcess(process);

                    run.runAllLocally(false, false, force, writeTriplesLang);

                } else {
                    if (triplify || upload) {
                        // log the user in
                        if (username == null || username.equals("") || password == null || password.equals("")) {
                            FimsPrinter.out.println("Need valid username / password for uploading");
                            helpf.printHelp("fims ", options, true);
                            return;
                        } else {
                            User user = userService.getUser(username, password);
                            if (user == null) {
                                FimsPrinter.out.println("Invalid username and/or password");
                                return;
                            }
                            // Check that a dataset code has been entered
                            if (!cl.hasOption("e")) {
                                FimsPrinter.out.println("Need to enter a dataset code before  uploading");
                                helpf.printHelp("fims ", options, true);
                                return;
                            }
                        }
                    }


                    // Now Run the process
                    Process process;
                    // use local configFile if specified
                    if (cl.hasOption("configFile")) {
                        System.out.println("using local config file = " + cl.getOptionValue("configFile").toString());
                        // Create the process object --- this is done each time to orient the application
                        process = new Process.ProcessBuilder(FimsMetadataFileManager, processController)
                                .addFmProperties(fmProps)
                                .configFile(new File(cl.getOptionValue("configFile")))
                                .build();

                    } else {
                        File configFile = new ConfigurationFileFetcher(projectId, output_directory, false).getOutputFile();
                        process = new Process.ProcessBuilder(FimsMetadataFileManager, processController)
                                .addFmProperties(fmProps)
                                .configFile(configFile)
                                .build();
                    }

                    FimsPrinter.out.println("Initializing ...");
                    FimsPrinter.out.println("\tinputFilename = " + input_file);

                    // Run the processor
                    processController.setProcess(process);

                    run.runAllLocally(upload, true, force, writeTriplesLang);
                }
            }
        } catch (Exception e) {
            FimsPrinter.out.println("\nError: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
