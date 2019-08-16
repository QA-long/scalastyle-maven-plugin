package org.scalastyle.maven.plugin;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.StringUtils;
import org.scalastyle.ConfigurationChecker;
import org.scalastyle.FileSpec;
import org.scalastyle.Level;
import org.scalastyle.Message;
import org.scalastyle.ScalastyleConfiguration;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Generate a report based on ScalastyleResults.
 *
 * @version $Id$
 */
public class ScalastyleReportGenerator {
    private Log log;


    private final Sink sink;

    private boolean enableRulesSummary;

    private boolean enableSeveritySummary;

    private boolean enableFilesSummary;

    private Level severityLevel;

    private ScalastyleConfiguration scalastyleConfig;


    public ScalastyleReportGenerator(Sink sink, ScalastyleConfiguration scalastyleConfig) {
        this.sink = sink;

        this.enableRulesSummary = true;
        this.enableSeveritySummary = true;
        this.enableFilesSummary = true;

        this.scalastyleConfig = scalastyleConfig;
    }

    public boolean isEnableRulesSummary() {
        return enableRulesSummary;
    }

    public void setEnableRulesSummary(boolean enableRulesSummary) {
        this.enableRulesSummary = enableRulesSummary;
    }

    public boolean isEnableSeveritySummary() {
        return enableSeveritySummary;
    }

    public void setEnableSeveritySummary(boolean enableSeveritySummary) {
        this.enableSeveritySummary = enableSeveritySummary;
    }

    public boolean isEnableFilesSummary() {
        return enableFilesSummary;
    }

    public void setEnableFilesSummary(boolean enableFilesSummary) {
        this.enableFilesSummary = enableFilesSummary;
    }

    public Level getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(Level severityLevel) {
        this.severityLevel = severityLevel;
    }


    public Log getLog() {
        if (this.log == null) {
            this.log = new SystemStreamLog();
        }
        return this.log;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void generateReport(ScalastyleResults results) {
        doHeading();

        if (enableSeveritySummary) {
            doSeveritySummary(results);
        }

        if (enableFilesSummary) {
            doFilesSummary(results);
        }

        if (enableRulesSummary) {
            doRulesSummary(results);
        }

        doDetails(results);
        sink.body_();
        sink.flush();
        sink.close();
    }


    private void doHeading() {
        sink.head();
        sink.title();
        sink.text("Scalastyle Results");
        sink.title_();
        sink.head_();

        sink.body();

        sink.section1();
        sink.sectionTitle1();
        sink.text("Scalastyle Results");
        sink.sectionTitle1_();

        sink.paragraph();
        sink.text("The following document contains the results of ");
        sink.link("http://www.scalastyle.org/");
        sink.text("Scalastyle");
        sink.link_();
        String version = getScalastyleVersion();
        if (version != null) {
            sink.text(" ");
            sink.text(version);
        }
//        sink.text(" ");
//        sink.text(String.format("with %s ruleset", "TODO"));
        sink.text(".");


        sink.paragraph_();
        sink.section1_();
    }

    /**
     * Create the rules summary section of the report.
     *
     * @param results The results to summarize
     */
    private void doRulesSummary(ScalastyleResults results) {
        if ( scalastyleConfig == null )
        {
            return;
        }

        sink.section1();
        sink.sectionTitle1();
        sink.text("Rules");
        sink.sectionTitle1_();

        sink.table();

        sink.tableRow();
        sink.tableHeaderCell();
        sink.text("Category");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text("Rule");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text("Violations");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text("Severity");
        sink.tableHeaderCell_();

        sink.tableRow_();

        // Top level should be the checker.
        if (scalastyleConfig.checks().size() > 0) {
            String category = null;
            int len = scalastyleConfig.checks().size();
            ConfigurationChecker[] checkers = new ConfigurationChecker[len];
            scala.collection.Iterator<ConfigurationChecker> it =scalastyleConfig.checks().iterator();
            int index=0;
            while(it.hasNext()){
                checkers[index++]=it.next();
            }
            Arrays.sort(checkers, new Comparator<ConfigurationChecker>() {
                public int compare(ConfigurationChecker o1, ConfigurationChecker o2) {
                    if(o1.className().equalsIgnoreCase(o2.className())){
                        return 0;
                    }
                    return o1.className().compareTo(o2.className());
                }
            });

            for(ConfigurationChecker checker :checkers){
                if(results.violations(RuleUtil.getName(checker.className()))>0){
                    doRuleRow(checker, results, category);
                    category = RuleUtil.getCategory(checker.className());
                }
            }
        } else {
            sink.tableRow();
            sink.tableCell();
            sink.text("No rules found");
            sink.tableCell_();
            sink.tableRow_();
        }

        sink.table_();

        sink.section1_();
    }

    /**
     * Create a summary for one Scalastyle rule.
     *
     * @param checker              The configuration reference for the row
     * @param results          The results to summarize
     * @param previousCategory The previous row's category
     */
    private void doRuleRow(ConfigurationChecker checker, ScalastyleResults results, String previousCategory) {

        String ruleName = RuleUtil.getName(checker.className());

        sink.tableRow();

        // column 1: rule category
        sink.tableCell();
        String category = RuleUtil.getCategory(checker.className());
        if (!category.equals(previousCategory)) {
            sink.text(category);
        }
        sink.tableCell_();

        // column 2: Rule name + configured attributes
        sink.tableCell();
        if (!"extension".equals(category)) {
            sink.link("http://checkstyle.sourceforge.net/config_" + category + ".html#" + ruleName);
            sink.text(ruleName);
            sink.link_();
        } else {
            sink.text(ruleName);
        }

        sink.tableCell_();

        // column 3: rule violation count
        sink.tableCell();

        sink.text(String.valueOf(results.violations(ruleName)));
        sink.tableCell_();

        // column 4: severity
        sink.tableCell();
        sink.text(checker.level().name());
        sink.tableCell_();

        sink.tableRow_();
    }

    private void doSeveritySummary(ScalastyleResults results) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Summary");
        sink.sectionTitle1_();

        sink.table();

        sink.tableRow();
        sink.tableHeaderCell();
        sink.text("Files");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
//        iconTool.iconInfo( IconTool.TEXT_TITLE );
        sink.text("Info");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
//        iconTool.iconWarning( IconTool.TEXT_TITLE );
        sink.text("Warning");
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
//        iconTool.iconError( IconTool.TEXT_TITLE );
        sink.text("Error");
        sink.tableHeaderCell_();
        sink.tableRow_();

        sink.tableRow();
        sink.tableCell();
        sink.text(String.valueOf(results.getFileCount()));
        sink.tableCell_();
        sink.tableCell();
        sink.text(String.valueOf(results.getSeverityCount(Level.apply(Level.Info()))));
        sink.tableCell_();
        sink.tableCell();
        sink.text(String.valueOf(results.getSeverityCount(Level.apply(Level.Warning()))));
        sink.tableCell_();
        sink.tableCell();
        sink.text(String.valueOf(results.getSeverityCount(Level.apply(Level.Error()))));
        sink.tableCell_();
        sink.tableRow_();

        sink.table_();

        sink.section1_();
    }

    private void doFilesSummary(ScalastyleResults results) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Files");
        sink.sectionTitle1_();

        sink.table();

        sink.tableRow();
        sink.tableHeaderCell();
        sink.text("File");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
//        iconTool.iconInfo( IconTool.TEXT_ABBREV );
        sink.text("Info");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
//        iconTool.iconWarning( IconTool.TEXT_ABBREV );
        sink.text("Warning");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
//        iconTool.iconError( IconTool.TEXT_ABBREV );
        sink.text("Error");
        sink.tableHeaderCell_();
        sink.tableRow_();

        // Sort the files before writing them to the report
        List<String> fileList = new ArrayList<String>(results.getFiles().keySet());
        Collections.sort(fileList);

        for (String filename : fileList) {
            List<AuditEvent> violations = results.getFileViolations(filename);
            if (violations.isEmpty()) {
                // skip files without violations
                continue;
            }

            sink.tableRow();

            sink.tableCell();
            sink.link("#" + filename.replace('/', '.'));
            sink.text(filename);
            sink.link_();
            sink.tableCell_();

            sink.tableCell();
            sink.text(String.valueOf(results.getSeverityCount(violations, Level.apply(Level.Info()))));
            sink.tableCell_();

            sink.tableCell();
            sink.text(String.valueOf(results.getSeverityCount(violations, Level.apply(Level.Warning()))));
            sink.tableCell_();

            sink.tableCell();
            sink.text(String.valueOf(results.getSeverityCount(violations, Level.apply(Level.Error()))));
            sink.tableCell_();

            sink.tableRow_();
        }

        sink.table_();
        sink.section1_();
    }


    private void doDetails(ScalastyleResults results) {

        sink.section1();
        sink.sectionTitle1();
        sink.text("Details");
        sink.sectionTitle1_();

        // Sort the files before writing their details to the report
        List<String> fileList = new ArrayList<String>(results.getFiles().keySet());
        Collections.sort(fileList);

        for (String file : fileList) {
            List<AuditEvent> violations = results.getFileViolations(file);

            if (violations.isEmpty()) {
                // skip files without violations
                continue;
            }

            sink.section2();
            SinkEventAttributes attrs = new SinkEventAttributeSet();
            attrs.addAttribute(SinkEventAttributes.ID, file.replace('/', '.'));
            sink.sectionTitle(Sink.SECTION_LEVEL_2, attrs);
            sink.text(file);
            sink.sectionTitle_(Sink.SECTION_LEVEL_2);

            sink.table();
            sink.tableRow();
            sink.tableHeaderCell();
            sink.text("Severity");
            sink.tableHeaderCell_();
            sink.tableHeaderCell();
            sink.text("Category");
            sink.tableHeaderCell_();
            sink.tableHeaderCell();
            sink.text("Rule");
            sink.tableHeaderCell_();
            sink.tableHeaderCell();
            sink.text("Message");
            sink.tableHeaderCell_();
            sink.tableHeaderCell();
            sink.text("Line");
            sink.tableHeaderCell_();
            sink.tableRow_();

            doFileEvents(violations, file);

            sink.table_();
            sink.section2_();
        }

        sink.section1_();
    }

    private void doFileEvents(List<AuditEvent> eventList, String filename) {
        for (AuditEvent event : eventList) {
            Level level = event.getSeverityLevel();

            if ((getSeverityLevel() != null) && !(getSeverityLevel() != level)) {
                continue;
            }

            sink.tableRow();

            sink.tableCell();
            sink.text(level.name());
            sink.tableCell_();

            sink.tableCell();
            String category = RuleUtil.getCategory(event);
            if (category != null) {
                sink.text(category);
            }
            sink.tableCell_();

            sink.tableCell();
            String ruleName = RuleUtil.getName(event);
            if (ruleName != null) {
                sink.text(ruleName);
            }
            sink.tableCell_();

            sink.tableCell();
            sink.text(event.getMessage());
            sink.tableCell_();

            sink.tableCell();

            int line = event.getLine();
//            if (getXrefLocation() != null && line != 0) {
//                sink.link(getXrefLocation() + "/" + filename.replaceAll("\\.java$", ".html") + "#L"
//                        + line);
//                sink.text(String.valueOf(line));
//                sink.link_();
//            } else if (line != 0) {
            sink.text(String.valueOf(line));
//            }
            sink.tableCell_();

            sink.tableRow_();
        }
    }


    /**
     * Get the effective Scalastyle version at runtime.
     *
     * @return the MANIFEST implementation version of Scalastyle API package (can be <code>null</code>)
     */
    private String getScalastyleVersion() {
        Package checkstyleApiPackage = ScalastyleConfiguration.class.getPackage();

        return (checkstyleApiPackage == null) ? null : checkstyleApiPackage.getImplementationVersion();
    }
}
