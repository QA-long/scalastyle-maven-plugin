package org.scalastyle.maven.plugin;

import org.scalastyle.FileSpec;
import org.scalastyle.Level;
import org.scalastyle.Message;
import org.scalastyle.StyleError;
import org.scalastyle.StyleException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Message<FileSpec> Wapper
 */
public class ScalastyleResults {
    private Map<String, List<AuditEvent>> files;

    public ScalastyleResults(List<Message<FileSpec>> messages) {
        files = new HashMap<String, List<AuditEvent>>();

        this.parse(messages);
    }

    public int getFileCount() {
        return this.files.size();
    }

    public Map<String, List<AuditEvent>> getFiles() {
        return files;
    }


    public long getSeverityCount(Level level) {
        long count = 0;

        for (List<AuditEvent> errors : this.files.values()) {
            count = count + getSeverityCount(errors, level);
        }

        return count;
    }

    public long getSeverityCount(String file, Level level) {
        long count = 0;

        if (!this.files.containsKey(file)) {
            return count;
        }

        List<AuditEvent> violations = this.files.get(file);

        count = getSeverityCount(violations, level);

        return count;
    }

    public long getSeverityCount(List<AuditEvent> violations, Level level) {
        long count = 0;

        for (AuditEvent event : violations) {
            if (event.getSeverityLevel().equals(level)) {
                count++;
            }
        }

        return count;
    }

    public int violations(String checker) {
        int count = 0;
        List<AuditEvent> violations = new ArrayList<AuditEvent>();

        for (Map.Entry<String, List<AuditEvent>> entry : files.entrySet()) {
            for (AuditEvent auditEvent : entry.getValue()) {
                if (checker.equalsIgnoreCase(RuleUtil.getName(auditEvent))) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getViolations() {
        int count = 0;
        List<AuditEvent> violations = new ArrayList<AuditEvent>();

        for (Map.Entry<String, List<AuditEvent>> entry : files.entrySet()) {
            violations.addAll(entry.getValue());
        }
        return count;
    }

    public List<AuditEvent> getFileViolations(String file) {
        List<AuditEvent> violations;

        if (this.files.containsKey(file)) {
            violations = this.files.get(file);
        } else {
            violations = new LinkedList<AuditEvent>();
            if (file != null) {
                this.files.put(file, violations);
            }
        }

        return violations;
    }

    public void parse(List<Message<FileSpec>> messages) {
        for (Message<FileSpec> message : messages) {
            if (message instanceof StyleError) {
                StyleError<FileSpec> msg = (StyleError<FileSpec>) message;
                List<AuditEvent> violations = new ArrayList<AuditEvent>();
                String fileName = msg.fileSpec().name();
                if (files.containsKey(fileName)) {
                    violations = files.get(fileName);
                }

                AuditEvent auditEvent = new AuditEvent();
                auditEvent.setFileName(fileName);
                if (msg.lineNumber().isDefined()) {
                    auditEvent.setLine(Integer.valueOf(msg.lineNumber().get().toString()));
                }
                auditEvent.setSeverityLevel(msg.level());
                auditEvent.setSourceName(msg.clazz().getName());
                if (msg.customMessage().isDefined()) {
                    auditEvent.setMessage(msg.customMessage().get());
                }
                violations.add(auditEvent);
                files.put(fileName, violations);
            } else if (message instanceof StyleException) {
                StyleException<FileSpec> msg = (StyleException<FileSpec>) message;
                List<AuditEvent> violations = new ArrayList<AuditEvent>();
                String fileName = msg.fileSpec().name();
                if (files.containsKey(fileName)) {
                    violations = files.get(fileName);
                }

                AuditEvent auditEvent = new AuditEvent();
                auditEvent.setFileName(fileName);
                if (msg.lineNumber().isDefined()) {
                    auditEvent.setLine(Integer.valueOf(msg.lineNumber().get().toString()));
                }
                auditEvent.setSeverityLevel(Level.apply(Level.Error()));
                if (msg.clazz().isDefined()) {
                    auditEvent.setSourceName(msg.clazz().get().getName());
                }
                auditEvent.setMessage(msg.message());
                violations.add(auditEvent);
                files.put(fileName, violations);
            } else {
                //"Nothing to do"
            }
        }
    }
}
