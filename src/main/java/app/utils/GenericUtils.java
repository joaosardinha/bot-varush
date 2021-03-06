package app.utils;

import net.dv8tion.jda.core.EmbedBuilder;

import java.text.DecimalFormat;

public class GenericUtils {

    public final static String COMMAND_TRIGGER = "!br";
    public final static long OWNER_DISCORD_USER_ID = 233347968378339328L;
    public final static String VARUSH_DISCORD_SERVER = "https://discord.gg/5ZZDsXv";
    public final static String ERROR_TITLE = "Error...";
    public final static String ERROR_MESSAGE = "Oops, something wrong is not right";
    public final static String STREAMING_ROLE_NAME = "Streaming";
    public final static String PROBATION_ROLE_NAME = "Under Probation";
    public final static String ASSETS_URL = "https://raw.githubusercontent" +
            ".com/curliq/assets/master/championIcons/";
    public final static String ASSETS_PROFILE_URL = "https://raw.githubusercontent" +
            ".com/curliq/assets/master/playerIcons/";
    public final static int DISCORD_MESSAGE_MAX_CHAR = 1024;

    /**
     * Log something on the console
     *
     * @param o what to log
     */
    public static void log(Object o) {
        System.out.println(o);
    }

    /** Build a basic embed message with just one field */
    public static EmbedBuilder getBasicEmbedMessage(String title, String message) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.addField(title, message, false);
        return eb;
    }

    /** Round decimal to 2 cases */
    public static String roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return String.format("%.2f", Double.valueOf(twoDForm.format(d)));
    }

}