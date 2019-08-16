package org.scalastyle.maven.plugin;

/**
 * Tooling for Scalastyle rules conventions: names, categories.
 *
 * @author qalong
 * @since 1.1.0
 */
public class RuleUtil {
    private RuleUtil() {
        // hide utility class constructor
    }

    private static final String SCALARIFORM_PACKAGE = "org.scalastyle.scalariform";
    private static final String FILE_PACKAGE = "org.scalastyle.file";

    /**
     * Get the rule name from an audit event.
     *
     * @param event the audit event
     * @return the rule name, which is the class name without package and removed eventual "Check" suffix
     */
    public static String getName(AuditEvent event) {
        return getName(event.getSourceName());
    }

    /**
     * Get the rule name from an audit event source name.
     *
     * @param eventSrcName the audit event source name
     * @return the rule name, which is the class name without package and removed eventual "Check" suffix
     */
    public static String getName(String eventSrcName) {
        if (eventSrcName == null) {
            return null;
        }

        if (eventSrcName.endsWith("Checker")) {
            eventSrcName = eventSrcName.substring(0, eventSrcName.length() - 7);
        }

        return eventSrcName.substring(eventSrcName.lastIndexOf('.') + 1);
    }

    /**
     * Get the rule category from an audit event.
     *
     * @param event the audit event
     * @return the rule category, which is the last package name or "misc" or "extension"
     */
    public static String getCategory(AuditEvent event) {
        return getCategory(event.getSourceName());
    }

    /**
     * Get the rule category from an audit event source name.
     *
     * @param eventSrcName the audit event source name
     * @return the rule category, which is the last package name or "misc" or "extension"
     */
    public static String getCategory(String eventSrcName) {
        if (eventSrcName == null) {
            return null;
        }

        int end = eventSrcName.lastIndexOf('.');
        eventSrcName = end == -1 ? eventSrcName : eventSrcName.substring(0, end);

        if (SCALARIFORM_PACKAGE.equals(eventSrcName)) {
            return "scalariform";
        } else if (FILE_PACKAGE.equals(eventSrcName)) {
            return "file";
        }

        return eventSrcName.substring(eventSrcName.lastIndexOf('.') + 1);
    }

}
