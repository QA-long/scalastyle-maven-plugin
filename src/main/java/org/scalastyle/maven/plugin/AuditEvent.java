package org.scalastyle.maven.plugin;

import org.scalastyle.Level;

public class AuditEvent   {


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public Level getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(Level severityLevel) {
        this.severityLevel = severityLevel;
    }

    private String fileName = "";
    private int column;
    private int line;
    private Level severityLevel;
    private String sourceName;
    private String message;

    public AuditEvent() {
    }


    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
