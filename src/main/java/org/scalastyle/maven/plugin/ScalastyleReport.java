package org.scalastyle.maven.plugin;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.resource.ResourceManager;
import org.codehaus.plexus.resource.loader.FileResourceCreationException;
import org.codehaus.plexus.resource.loader.ResourceNotFoundException;
import org.scalastyle.Directory;
import org.scalastyle.FileSpec;
import org.scalastyle.Message;
import org.scalastyle.OutputResult;
import org.scalastyle.ScalastyleChecker;
import org.scalastyle.ScalastyleConfiguration;
import org.scalastyle.TextOutput;
import scala.Option;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * A reporting task that performs Scalastyle analysis and generates an HTML
 * report on any violations that Scalastyle finds
 *
 * @author <a href="mailto:qal0625@163.com">Emmanuel Venisse</a>
 * @version $Id$
 */
@Mojo(
        name = "scalastyle",
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class ScalastyleReport extends AbstractMavenReport {

    /**
     * <p>
     * Specifies the location of the scalstyle XML configuration file to use.
     * </p>
     * <p>
     * Potential values are a filesystem path, a URL, or a classpath resource.
     * </p>
     * <p>
     * This parameter is resolved as file, classpath resource then URL.
     * </p>
     * <p>
     * <b>default_config.xml</b> from scalastyle classpath jar is used if non is specified in configuration
     * </p>
     * <p/>
     */
    @Parameter(property = "scalastyle.config.location", required = true, defaultValue = "default_config.xml")
    private String configLocation;


    /**
     * Specifies the encoding of the Scalastyle (XML) output
     */
    @Parameter(property = "scalastyle.output.encoding")
    private String outputEncoding;


    /**
     * Print details of everything that Scalastyle is doing
     */
    @Parameter(property = "scalastyle.verbose", defaultValue = "false")
    private Boolean verbose = Boolean.FALSE;

    /**
     * Print very little.
     */
    @Parameter(property = "scalastyle.quiet", defaultValue = "false")
    private Boolean quiet = Boolean.FALSE;

    /**
     * Specifies the location of the Scala source directories to be used for
     * Scalastyle. This is only used if sourceDirectories is not specified
     */
    @Parameter
    private File sourceDirectory;

    /**
     * Specifies the location of the Scala source directories to be used for
     * Scalastyle.
     */
    @Parameter
    private File[] sourceDirectories;

    /**
     * Specifies the location of the Scala test source directories to be used
     * for Scalastyle. Only used if testSourceDirectories is not specified
     */
    @Parameter
    private File testSourceDirectory;

    /**
     * Specifies the location of the Scala test source directories to be used
     * for Scalastyle.
     */
    @Parameter
    private File[] testSourceDirectories;

    /**
     * Include or not the test source directory in the Scalastyle checks.
     */
    @Parameter(property = "scalastyle.includeTestSourceDirectory", defaultValue = "false")
    private Boolean includeTestSourceDirectory = Boolean.FALSE;

    /**
     * Directory containing the build files.
     */
    @Parameter(property = "scalastyle.build.directory", defaultValue = "${project.build.directory}")
    private File buildDirectory;

    /**
     * Base directory of the project.
     */
    @Parameter(property = "scalastyle.base.directory", defaultValue = "${basedir}")
    private File baseDirectory;

    /**
     * Specifies the encoding of the source files
     */
    @Parameter(property = "scalastyle.input.encoding")
    private String inputEncoding;

    @Component
    private ResourceManager resourceManager;

    /**
     * Specifies if the Rules summary should be enabled or not.
     */
    @Parameter( property = "scalastyle.enable.rules.summary", defaultValue = "true" )
    private boolean enableRulesSummary;

    /**
     * Specifies if the Severity summary should be enabled or not.
     */
    @Parameter( property = "scalastyle.enable.severity.summary", defaultValue = "true" )
    private boolean enableSeveritySummary;

    /**
     * Specifies if the Files summary should be enabled or not.
     */
    @Parameter( property = "scalastyle.enable.files.summary", defaultValue = "true" )
    private boolean enableFilesSummary;

    private long now() {
        return new Date().getTime();
    }

    private String getConfigFile(String configLocation) throws MojoFailureException {
        if (configLocation == null) {
            throw new MojoFailureException("configLocation is required");
        }

        if (new File(configLocation).exists()) {
            return configLocation;
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClassLoaderWithProjectResources());
            File configFile = resourceManager.getResourceAsFile(configLocation);
            if (configFile == null) {
                throw new MojoFailureException("Unable to process configuration file at location " + configLocation);
            }
            return configFile.getAbsolutePath();
        } catch (ResourceNotFoundException e) {
            throw new MojoFailureException("Unable to find configuration file at location " + configLocation);
        } catch (FileResourceCreationException e) {
            throw new MojoFailureException("Unable to process configuration file at location " + configLocation, e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        try {
            ScalastyleConfiguration configuration = ScalastyleConfiguration.readFromXml(getConfigFile(configLocation));
            long start = now();
            final scala.Option<ClassLoader> none = scala.Option$.MODULE$.apply(null);
            ScalastyleChecker<FileSpec> sc = new ScalastyleChecker<FileSpec>(none);

            List<Message<FileSpec>> messages = sc.checkFilesAsJava(configuration, getFilesToProcess());

            ScalastyleResults results = new ScalastyleResults(messages);
            ScalastyleReportGenerator generator = new ScalastyleReportGenerator(getSink(),configuration);
            generator.setLog( getLog() );
            generator.setEnableRulesSummary( enableRulesSummary );
            generator.setEnableSeveritySummary( enableSeveritySummary );
            generator.setEnableFilesSummary( enableFilesSummary );
            generator.generateReport(results);
        } catch (Exception e) {
            throw new MavenReportException("Failed during scalastyle execution", e);
        }
    }

    public String getOutputName() {
        return "scalastyle";
    }

    public String getName(Locale locale) {
        return "Scalastyle";
    }

    public String getDescription(Locale locale) {
        return "Report on coding style conventions.";
    }

    private URLClassLoader getClassLoaderWithProjectResources() throws MojoFailureException {
        List<String> classPathStrings = new ArrayList<String>();
        List<URL> urls = new ArrayList<URL>(classPathStrings.size());

        try {
            classPathStrings.addAll(project.getTestCompileSourceRoots());
            classPathStrings.addAll(project.getCompileSourceRoots());

            for (Resource resource : project.getTestResources()) {
                classPathStrings.add(resource.getDirectory());
            }
            for (Resource resource : project.getResources()) {
                classPathStrings.add(resource.getDirectory());
            }
            for (String path : classPathStrings) {
                urls.add(new File(path).toURI().toURL());
            }
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        return new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
    }

    private List<FileSpec> getFilesToProcess() {
        List<FileSpec> all = new ArrayList<FileSpec>();

        all.addAll(getFiles("sourceDirectory", sourceDirectoriesAsList(), inputEncoding));
        all.addAll(getFiles("testSourceDirectory", testSourceDirectoriesAsList(), inputEncoding));

        return all;
    }

    private List<FileSpec> getFiles(String name, List<File> dirs, String encoding) {
        List<FileSpec> files = new ArrayList<FileSpec>();

        for (File dir : dirs) {
            if (isDirectory(dir)) {
                getLog().debug("processing " + name + "=" + dir + " encoding=" + encoding);
                files.addAll(Directory.getFilesAsJava(Option.apply(encoding), Collections.singletonList(dir)));
            } else {
                getLog().warn(name + " is not specified or does not exist value=" + dir);
            }
        }

        return files;
    }

    private boolean isDirectory(File file) {
        return file != null && file.exists() && file.isDirectory();
    }

    private List<File> sourceDirectoriesAsList() {
        return arrayOrValue(sourceDirectories, sourceDirectory);
    }

    private List<File> testSourceDirectoriesAsList() {
        return (!includeTestSourceDirectory) ? new LinkedList<File>() : arrayOrValue(testSourceDirectories, testSourceDirectory);
    }

    private List<File> arrayOrValue(File[] array, File value) {
        return (array != null) ? Arrays.asList(array) : Collections.singletonList(value);
    }
}
