package me.dags.discordsync;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageProcessor {

    private static final Pattern MENTION = Pattern.compile("@([^ ]+)");

    private static final MentionType[] MENTION_TYPES = {
            new MentionType("everyone"),
            new MentionType("here"),
            new MentionType("user", true),
    };

    public static String processContent(Predicate<String> source, String content) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = MessageProcessor.MENTION.matcher(content);

        while (matcher.find()) {
            String group = matcher.group(matcher.groupCount());
            for (MentionType mentionType : MENTION_TYPES) {
                if (mentionType.matches(group) && !mentionType.isPermitted(source)) {
                    matcher.appendReplacement(sb, group);
                    break;
                }
            }
        }

        return matcher.appendTail(sb).toString();
    }

    private static class MentionType {

        private final boolean any;
        private final String type;
        private final String permission;

        private MentionType(String type) {
            this(type, false);
        }

        private MentionType(String type, boolean any) {
            this.any = any;
            this.type = type;
            this.permission = DiscordSync.ID + ".mention." + type;
        }

        private boolean matches(String text) {
            return any || text.equalsIgnoreCase(type);
        }

        private boolean isPermitted(Predicate<String> source) {
            return source != null && source.test(permission);
        }
    }
}
