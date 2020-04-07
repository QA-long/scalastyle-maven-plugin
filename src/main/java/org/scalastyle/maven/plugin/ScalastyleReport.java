package org.scalastyle.maven.plugin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.model.Resource;
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
import org.scalastyle.MessageHelper;
import org.scalastyle.ScalastyleChecker;
import org.scalastyle.ScalastyleConfiguration;
import org.scalastyle.StyleError;
import org.scalastyle.StyleException;
import org.scalastyle.XmlOutput;
import scala.Option;
import scala.xml.Elem;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * A reporting task that performs Scalastyle analysis and generates an HTML
 * report on any violations that Scalastyle finds
 * @author <a href="mailto:qal0625@163.com">Emmanuel Venisse</a>
 * @version $Id$
 */
@Mojo(
        name="scalastyle",
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class ScalastyleReport extends AbstractMavenReport {

    @Parameter(property = "scalastyle.config.location", required = true, defaultValue = "default_config.xml")
    private String configLocation;

    @Component
    private ResourceManager resourceManager;
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
     * Specifies the encoding of the source files
     */
    @Parameter(property = "scalastyle.input.encoding")
    private String inputEncoding;

    /**
     * Specifies the encoding of the Scalastyle (XML) output
     */
    @Parameter(property = "scalastyle.output.encoding")
    private String outputEncoding;

    /**
     * Specifies the path and filename to save the Scalastyle output.
     */
    @Parameter(property = "scalastyle.output.file")
    private File outputFile;

    private URLClassLoader getClassLoaderWithProjectResources() throws MojoFailureException {
        List<String> classPathStrings = new ArrayList<String>();
        List<URL> urls = new ArrayList<URL>( classPathStrings.size() );

        try {
            classPathStrings.addAll(project.getTestCompileSourceRoots());
            classPathStrings.addAll(project.getCompileSourceRoots());

            for(Resource resource:project.getTestResources()){
                classPathStrings.add(resource.getDirectory());
            }
            for(Resource resource:project.getResources()){
                classPathStrings.add(resource.getDirectory());
            }
            for ( String path : classPathStrings ){
                urls.add(new File(path).toURI().toURL());
            }
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(),e);
        }

        return new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
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
            if ( configFile == null ){
                throw new MojoFailureException("Unable to process configuration file at location "+ configLocation);
            }
            return configFile.getAbsolutePath();
        } catch (ResourceNotFoundException e) {
            throw new MojoFailureException("Unable to find configuration file at location "+ configLocation);
        } catch (FileResourceCreationException e) {
            throw new MojoFailureException("Unable to process configuration file at location "+ configLocation,e);
        }finally {
            Thread.currentThread().setContextClassLoader(original);
        }

    }

    private boolean isDirectory(File file) {
        return file != null && file.exists() && file.isDirectory();
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


    private List<File> sourceDirectoriesAsList() {
        return arrayOrValue(sourceDirectories, sourceDirectory);
    }

    private List<File> testSourceDirectoriesAsList() {
        return (!includeTestSourceDirectory) ? new LinkedList<File>() : arrayOrValue(testSourceDirectories, testSourceDirectory);
    }

    private List<File> arrayOrValue(File[] array, File value) {
        return (array != null) ? Arrays.asList(array) : Collections.singletonList(value);
    }

    private List<FileSpec> getFilesToProcess() {
        List<FileSpec> all = new ArrayList<FileSpec>();

        all.addAll(getFiles("sourceDirectory", sourceDirectoriesAsList(), inputEncoding));
        all.addAll(getFiles("testSourceDirectory", testSourceDirectoriesAsList(), inputEncoding));

        return all;
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        try {
            ScalastyleConfiguration configuration = ScalastyleConfiguration.readFromXml(getConfigFile(configLocation));
            long start = System.currentTimeMillis();
            final scala.Option<ClassLoader> none = scala.Option$.MODULE$.apply(null);
            ScalastyleChecker<FileSpec> sc = new ScalastyleChecker<FileSpec>(none);

            List<Message<FileSpec>> messages = sc.checkFilesAsJava(configuration, getFilesToProcess());
            String encoding = (outputEncoding != null) ? outputEncoding : System.getProperty("file.encoding");
            Config config = ConfigFactory.load(sc.getClass().getClassLoader());
            System.out.println("============0000000000000");
//            MessageHelper helper = new MessageHelper(config);
//            Elem elem = HtmlOutput.toCheckstyleFormat(helper,  messages);
//            System.out.println("============"+elem.text());
            generateReport(messages);
        }catch (Exception ex) {

        }

    }


    private void generateReport(List<Message<FileSpec>> results){

        doSeveritySummary(results);
        doFilesSummary(results);
        doDetails(results);
    }


    private void doSeveritySummary( List<Message<FileSpec>> results )
    {
        Sink sink =this.getSink();
        sink.section1();
        sink.sectionTitle1();
        sink.text( "Summary" );
        sink.sectionTitle1_();

        sink.table();

        sink.tableRow();
        sink.tableHeaderCell();
        sink.text( "Files");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text("Info");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text("Warning");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text("Error");
        sink.tableHeaderCell_();
        sink.tableRow_();

        sink.tableRow();
        sink.tableCell();
        sink.text(String.valueOf(results.size()));
        sink.tableCell_();
        sink.tableCell();
        sink.text( "Info" );
        sink.tableCell_();
        sink.tableCell();
        sink.text( "Warning" );
        sink.tableCell_();
        sink.tableCell();
        sink.text("Error");
        sink.tableCell_();
        sink.tableRow_();

        sink.table_();

        sink.section1_();
    }

    private void doFilesSummary(  List<Message<FileSpec>> results )
    {
        Sink sink =this.getSink();
        sink.section1();
        sink.sectionTitle1();
        sink.text( "Files" );
        sink.sectionTitle1_();

        sink.table();

        sink.tableRow();
        sink.tableHeaderCell();
        sink.text( "File");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( "Info" );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( "Warning" );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text("Error");
        sink.tableHeaderCell_();
        sink.tableRow_();

        for(Message<FileSpec> message : results){
            if(message instanceof StyleError){
                StyleError<FileSpec> msg =(StyleError<FileSpec>)message;
                System.out.println("fffff"+msg.fileSpec().name());
            }else  if(message instanceof StyleException){
                StyleException<FileSpec> msg =(StyleException<FileSpec>)message;
                System.out.println("gggggg"+msg.fileSpec().name());
            }else{
                System.out.println("类型不支持"+message);
            }
        }
        // Sort the files before writing them to the report
//        List<String> fileList = new ArrayList<>( results.getFiles().keySet() );
//        Collections.sort( fileList );
//
//        for ( String filename : fileList )
//        {
//            List<AuditEvent> violations = results.getFileViolations( filename );
//            if ( violations.isEmpty() )
//            {
//                // skip files without violations
//                continue;
//            }
//
//            sink.tableRow();
//
//            sink.tableCell();
//            sink.link( "#" + filename.replace( '/', '.' ) );
//            sink.text( filename );
//            sink.link_();
//            sink.tableCell_();
//
//            sink.tableCell();
//            sink.text( String.valueOf( results.getSeverityCount( violations, SeverityLevel.INFO ) ) );
//            sink.tableCell_();
//
//            sink.tableCell();
//            sink.text( String.valueOf( results.getSeverityCount( violations, SeverityLevel.WARNING ) ) );
//            sink.tableCell_();
//
//            sink.tableCell();
//            sink.text( String.valueOf( results.getSeverityCount( violations, SeverityLevel.ERROR ) ) );
//            sink.tableCell_();
//
//            sink.tableRow_();
//        }

        sink.table_();
        sink.section1_();
    }

    private void doDetails(  List<Message<FileSpec>>  results )
    {
        Sink sink =this.getSink();
        sink.section1();
        sink.sectionTitle1();
        sink.text("Details" );
        sink.sectionTitle1_();

        // Sort the files before writing their details to the report
//        List<String> fileList = new ArrayList<>( results.getFiles().keySet() );
//        Collections.sort( fileList );
//
//        for ( String file : fileList )
//        {
//            List<AuditEvent> violations = results.getFileViolations( file );
//
//            if ( violations.isEmpty() )
//            {
//                // skip files without violations
//                continue;
//            }
//
//            sink.section2();
//            SinkEventAttributes attrs = new SinkEventAttributeSet();
//            attrs.addAttribute( SinkEventAttributes.ID, file.replace( '/', '.' ) );
//            sink.sectionTitle( Sink.SECTION_LEVEL_2, attrs );
//            sink.text( file );
//            sink.sectionTitle_( Sink.SECTION_LEVEL_2 );
//
//            sink.table();
//            sink.tableRow();
//            sink.tableHeaderCell();
//            sink.text( bundle.getString( "report.checkstyle.column.severity" ) );
//            sink.tableHeaderCell_();
//            sink.tableHeaderCell();
//            sink.text( bundle.getString( "report.checkstyle.rule.category" ) );
//            sink.tableHeaderCell_();
//            sink.tableHeaderCell();
//            sink.text( bundle.getString( "report.checkstyle.rule" ) );
//            sink.tableHeaderCell_();
//            sink.tableHeaderCell();
//            sink.text( bundle.getString( "report.checkstyle.column.message" ) );
//            sink.tableHeaderCell_();
//            sink.tableHeaderCell();
//            sink.text( bundle.getString( "report.checkstyle.column.line" ) );
//            sink.tableHeaderCell_();
//            sink.tableRow_();
//
//            doFileEvents( violations, file );
//
//            sink.table_();
//            sink.section2_();
//        }

        sink.section1_();
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
}
