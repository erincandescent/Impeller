package com.atlassian.jconnect.droid.service;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents file attachment for a feedback item.
 * 
 * @since 1.0
 */
public class FeedbackAttachment {
    /**
     * Persistent attachment file that should not be modified.
     * 
     * @param name
     *            name of the attachment
     * @param sourcePath
     *            path to the attachment file
     * @return new persistent feedback attachment
     */
    public static FeedbackAttachment persistent(String name, String sourcePath) {
        return new FeedbackAttachment(name, sourcePath, false);
    }

    /**
     * Temporary attachment file that should be deleted after sending.
     * 
     * @param name
     *            name of the attachment
     * @param sourcePath
     *            path to the attachment file
     * @return new temporary feedback attachment
     */
    public static FeedbackAttachment temporary(String name, String sourcePath) {
        return new FeedbackAttachment(name, sourcePath, true);
    }

    private final String name;
    private final File source;
    private final boolean temporary;

    private FeedbackAttachment(String name, File source, boolean temporary) {
        this.name = name;
        this.source = checkNotNull(source, "source");
        this.temporary = temporary;
    }

    private FeedbackAttachment(String name, String sourcePath, boolean temporary) {
        this.name = name;
        this.source = new File(sourcePath);
        this.temporary = temporary;
    }

    /**
     * Name of the attachment part.
     * 
     * @return name of the attachment
     */
    public String getName() {
        return name;
    }

    /**
     * Source file of the attachment.
     * 
     * @return source file
     */
    public File getSource() {
        return source;
    }

    public boolean exists() {
        return source.exists();
    }

    public boolean isTemporary() {
        return temporary;
    }
}
