package com.atlassian.jconnect.droid.jira;

import java.util.Date;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Comment domain object.<br />
 * <strong>Important:</strong> The 'systemUser' is the JIRA user that all JMC
 * Mobile Devices will post as on the jira instance.
 * 
 * @since 1.0
 */
public class Comment implements Parcelable {

    public static final Comment EMPTY = new Comment(null, null, null);

    public static boolean isEmpty(Comment comment) {
        return EMPTY == comment;
    }

    private final String username;
    private final String text;
    private final Date date;
    private final boolean systemUser;

    public Comment(String username, String text, Date date, boolean systemUser) {
        this.username = username;
        this.text = text;
        this.date = date;
        this.systemUser = systemUser;
    }

    public Comment(String username, String text, Date date) {
        this(username, text, date, false);
    }

    public String getUsername() {
        return username;
    }

    public String getText() {
        return text;
    }

    public Date getDate() {
        return date;
    }

    public boolean isSystemUser() {
        return systemUser;
    }

    public static final Creator<Comment> CREATOR = new Creator<Comment>() {
        @Override
        public Comment createFromParcel(Parcel source) {
            return new Comment(source.readString(), source.readString(), new Date(source.readLong()), source.readInt() == 1);
        }

        @Override
        public Comment[] newArray(int size) {
            return new Comment[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(username);
        dest.writeString(text);
        dest.writeLong(date.getTime());
        dest.writeInt(systemUser ? 1 : 0);
    }
}
