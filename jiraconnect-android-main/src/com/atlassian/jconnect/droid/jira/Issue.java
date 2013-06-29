package com.atlassian.jconnect.droid.jira;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

/**
 * Represents issue retrieved from remote JIRA/JMC server.
 * 
 * @since 1.0
 */
public class Issue implements Parcelable {

    private final String key;
    private final String status;
    private final String title;
    private final String description;
    private final Date dateUpdated;
    private final List<Comment> comments;
    private final boolean hasUpdates;

    public static final class Builder {
        private static final String TAG = Builder.class.getName();

        private final String key;
        private String status;
        private String title;
        private String description;
        private Date dateUpdated;
        private List<Comment> comments = Collections.emptyList();
        private boolean hasUpdates;

        public Builder(String key) {
            this.key = checkNotNull(key, "key");
        }

        public Builder(Issue existing) {
            this(existing.getKey());
            status(existing.getStatus());
            title(existing.getTitle());
            description(existing.getDescription());
            dateUpdated(existing.getDateUpdated());
            comments(existing.getComments());
            hasUpdates(existing.hasUpdates());
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder dateUpdated(Date dateUpdated) {
            this.dateUpdated = dateUpdated;
            return this;
        }

        public Builder comments(Iterable<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder addComment(Comment comment) {
            this.comments = ImmutableList.<Comment> builder().addAll(comments).add(comment).build();
            return this;
        }

        public Builder hasUpdates(boolean hasUpdates) {
            this.hasUpdates = hasUpdates;
            return this;
        }

        public Issue build() {
            if (dateUpdated == null) {
                Log.w(TAG, "You tried to build an issue but provided no 'dateUpdated'. Defaulting the updated date to right now.");
                dateUpdated = new Date();
            }
            return new Issue(this);
        }

    }

    private Issue(Builder builder) {
        this.key = builder.key;
        this.status = builder.status;
        this.title = builder.title;
        this.description = builder.description;
        this.dateUpdated = builder.dateUpdated;
        this.hasUpdates = builder.hasUpdates;
        this.comments = builder.comments;
    }

    public String getKey() {
        return key;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public boolean hasUpdates() {
        return hasUpdates;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !Issue.class.equals(obj.getClass())) {
            return false;
        }
        Issue that = (Issue) obj;
        return this.key.equals(that.key);
    }

    public static final Creator<Issue> CREATOR = new Creator<Issue>() {
        @Override
        public Issue createFromParcel(Parcel source) {
            return new Builder(source.readString()).status(source.readString())
                    .title(source.readString())
                    .description(source.readString())
                    .dateUpdated(readDate(source))
                    .comments(source.readArrayList(getClass().getClassLoader()))
                    .hasUpdates(source.readInt() == 1)
                    .build();
        }

        @Override
        public Issue[] newArray(int size) {
            return new Issue[size];
        }

        private Date readDate(Parcel source) {
            final long time = source.readLong();
            if (time != Long.MIN_VALUE) {
                return new Date(time);
            } else {
                return null;
            }
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(key);
        dest.writeString(status);
        dest.writeString(title);
        dest.writeString(description);
        if (dateUpdated != null) {
            dest.writeLong(dateUpdated.getTime());
        } else {
            dest.writeLong(Long.MIN_VALUE);
        }
        dest.writeList(comments);
        dest.writeInt(hasUpdates ? 1 : 0);
    }

    public static final class Orderings {

        private static final Ordering<Issue> BY_KEY = new Ordering<Issue>() {
            @Override
            public int compare(Issue first, Issue second) {
                if (first == null && second == null) {
                    return 0;
                } else if (first == null) {
                    return -1; // nulls last
                } else if (second == null) {
                    return 1; // nulls last
                } else {
                    return first.key.compareTo(second.key);
                }
            }
        };

        public static Ordering<Issue> byKey() {
            return BY_KEY;
        }
    }
}
