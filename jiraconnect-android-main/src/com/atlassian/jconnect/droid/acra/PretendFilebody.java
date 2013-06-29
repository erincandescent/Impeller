package com.atlassian.jconnect.droid.acra;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.http.entity.mime.content.StringBody;

/**
 * This is an explanation of why this class is required.<br />
 * A {@link StringBody} will return null when {@link StringBody#getFilename()}
 * is called. When that file gets passed on to JIRA it attempts to look for a
 * filename, does not find one and thus does not give the attatchment to the
 * issue. It ignore it. This change makes sure that there is a filename on the
 * message that gets sent so that the attatchment is actually made in JIRA.
 * 
 * @author rmassaioli
 * 
 */
class PretendFileBody extends StringBody {
    private final String fileName;

    public PretendFileBody(String fileName, String fileBody) throws UnsupportedEncodingException {
        super(fileBody, "text/plain", Charset.forName("UTF-8"));
        this.fileName = fileName;
    }

    @Override
    public String getFilename() {
        return fileName;
    }
}
