package eu.e43.impeller.uikit;

import android.content.Context;
import android.content.res.Resources;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;

import eu.e43.impeller.R;

/**
 * Created by oshepherd on 18/04/2014.
 */
public class ActivityUtils {
    static final private ImmutableMap<String, Integer> ms_verbStrings;
    static final private ImmutableMap<String, Integer> ms_objectTypeStrings;

    static {
        ms_verbStrings = ImmutableMap.<String, Integer>builder()
                .put("post", R.string.verb_posted)
                .put("follow", R.string.verb_followed)
                .put("stop-following", R.string.verb_stopped_following)
                .put("favorite", R.string.verb_favorited)
                .put("unfavorite", R.string.verb_unfavorited)
                .put("share", R.string.verb_shared)
                .put("unshare", R.string.verb_unshared)
                .put("like", R.string.verb_liked)
                .put("unlike", R.string.verb_unliked)
                .put("create", R.string.verb_created)
                .put("add", R.string.verb_added)
                .put("delete", R.string.verb_deleted)
                .put("join", R.string.verb_joined)
                .put("remove", R.string.verb_removed)
                .put("leave", R.string.verb_left)
                .put("play", R.string.verb_played)
                .put("listen", R.string.verb_listened_to)
                .put("checkin", R.string.verb_checked_in_at)
                .put("update", R.string.verb_updated)
                .build();

        ms_objectTypeStrings = ImmutableMap.<String, Integer>builder()
                .put("note", R.string.object_type_a_note)
                .put("image", R.string.object_type_an_image)
                .put("comment", R.string.object_type_a_comment)
                .put("person", R.string.object_type_a_person)
                .put("group", R.string.object_type_a_group)
                .put("activity", R.string.object_type_an_activity)
                .put("place", R.string.object_type_a_place)
                .put("collection", R.string.object_type_a_collection)
                .put("review", R.string.object_type_a_review)
                .put("article", R.string.object_type_an_article)
                .put("video", R.string.object_type_a_video)
                .put("audio", R.string.object_type_an_audio)
                .put("service", R.string.object_type_a_service)
                .put("application", R.string.object_type_an_application)
                .put("game", R.string.object_type_a_game)
                .put("event", R.string.object_type_an_event)
                .put("file", R.string.object_type_a_file)
                .build();
    }

    static public String getVerb(Context ctx, String verb)
    {
        if(ms_verbStrings.containsKey(verb)) {
            return ctx.getString(ms_verbStrings.get(verb));
        } else return verb;
    }

    static public String localizedDescription(Context ctx, JSONObject activity)
    {
        Resources resources = ctx.getResources();
        try {
            String verb = activity.optString("verb", "post").toLowerCase();
            String objectType =
                    activity.getJSONObject("object").optString("objectType", "note").toLowerCase();

            if(ms_verbStrings.containsKey(verb) && ms_objectTypeStrings.containsKey(objectType)) {
                String actorName, localizedVerb, objectTitle, inReplyToTitle;
                String actorUrl = activity.getJSONObject("actor").optString("url", null);

                if(actorUrl != null)
                    actorName = String.format("<a href='%s'>%s</a>", actorUrl,
                            activity.getJSONObject("actor").optString("displayName", resources.getString(R.string.actor_unknown)));
                else
                    actorName = activity.getJSONObject("actor").optString("displayName", resources.getString(R.string.actor_unknown));

                localizedVerb =  resources.getString(ms_verbStrings.get(verb));

                String objectUrl = activity.getJSONObject("object").optString("url", null);
                String objectDisplayName = activity.getJSONObject("object").optString("displayName", null);

                if(objectDisplayName != null) {
                    if(objectUrl != null) {
                        objectTitle = String.format("<a href='%s'>%s</a>", objectUrl, objectDisplayName);
                    } else {
                        objectTitle = objectDisplayName;
                    }
                } else if(objectUrl != null) {
                    objectTitle = String.format("<a href='%s'>%s</a>", objectUrl,
                            resources.getString(ms_objectTypeStrings.get(objectType)));
                } else {
                    objectTitle = resources.getString(ms_objectTypeStrings.get(objectType));
                }

                if(activity.getJSONObject("object").has("inReplyTo")) {
                    String replyToObjectType = activity.getJSONObject("object").getJSONObject("inReplyTo").optString("objectType", null).toLowerCase();
                    String replyToObjectUrl = activity.getJSONObject("object").getJSONObject("inReplyTo").optString("url", null);
                    String replyToObjectDisplayName = activity.getJSONObject("object").getJSONObject("inReplyTo").optString("displayName", null);

                    if(replyToObjectDisplayName != null) {
                        if(replyToObjectUrl != null) {
                            inReplyToTitle = String.format("<a href='%s'>%s</a>", replyToObjectUrl, replyToObjectDisplayName);
                        } else {
                            inReplyToTitle = replyToObjectDisplayName;
                        }
                    } else if(ms_objectTypeStrings.containsKey(replyToObjectType)) {
                        if(replyToObjectUrl != null)
                            inReplyToTitle = String.format("<a href='%s'>%s</a>",
                                    replyToObjectUrl, resources.getString(ms_objectTypeStrings.get(replyToObjectType)));
                        else
                            inReplyToTitle = resources.getString(ms_objectTypeStrings.get(replyToObjectType));
                    } else {
                        if(replyToObjectUrl != null)
                            inReplyToTitle = String.format("<a href='%s'>%s</a>", replyToObjectUrl, resources.getString(R.string.object_type_an_object));
                        else
                            inReplyToTitle = resources.getString(R.string.object_type_an_object);
                    }

                    return String.format(resources.getString(R.string.format_string_reply), actorName, localizedVerb, objectTitle, inReplyToTitle);
                } else {
                    return String.format(resources.getString(R.string.format_string_activity),
                            actorName, localizedVerb, objectTitle);
                }
            } else return activity.optString("content", "(Action string missing)");
        } catch(JSONException e) {
            return e.getLocalizedMessage();
        }

    }
}
