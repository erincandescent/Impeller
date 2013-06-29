package com.atlassian.jconnect.droid.jira;

import com.google.common.collect.ImmutableList;

import java.util.Collections;
import java.util.List;

/**
 * Represents JIRA response containing issues with comments.
 * 
 * @since 1.0
 */
public final class IssuesWithComments {

    public static final IssuesWithComments DUMMY = new IssuesWithComments(Collections.<Issue> emptyList(), 0L);

    public static boolean isDummyIssues(IssuesWithComments issues) {
        return DUMMY.equals(issues);
    }

    private final List<Issue> issues;
    private final long lastUpdated;

    public IssuesWithComments(List<Issue> issues, long lastUpdated) {
        this.issues = ImmutableList.copyOf(issues);
        this.lastUpdated = lastUpdated;
    }

    public List<Issue> issues() {
        return ImmutableList.copyOf(issues);
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public long lastUpdated() {
        return lastUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IssuesWithComments that = (IssuesWithComments) o;
        return this.lastUpdated == that.lastUpdated && this.issues.equals(that.issues);
    }

    @Override
    public int hashCode() {
        int result = issues.hashCode();
        result = 31 * result + (int) (lastUpdated ^ (lastUpdated >>> 32));
        return result;
    }
}
