package app;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import com.google.gson.Gson;

import app.rest.twitch.TwitchInterface;
import app.rest.twitch.pojos.StreamPOJO;
import app.utils.BattleriteUtils;
import app.utils.GenericUtils;
import app.utils.NetworkUtils;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.user.UserGameUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import retrofit2.Response;

/**
 * This class listens for changes in Game presence on every user in the server.
 * 
 * Purpose of this is to add a role called Streamer to whomever is streaming Battlerite at the moment and remove it
 * as soon as they finish the stream or stop playing Battlerite.
 * 
 * To use this create a role called {@value utils.GenericUtils.#STREAMER_ROLE_NAME} 
 * and pin it to the right hand side on your server.
 */
public class StreamingRoleListener extends ListenerAdapter {

    private final int RECALL_TWITCH_INTERVAL = 3 * 60 * 1000; // 3:00 minutes
    private final int TWITCH_CALL_DELAY = 30 * 1000; // 30 seconds

    private Role streamerRole;

    public StreamingRoleListener(JDA jda) {
        for (Member m : jda.getGuildsByName("battlerite", true).get(0).getMembers())
            runChecks(jda.getGuildsByName("battlerite", true).get(0), m, m.getGame());
    }

    /**
     * called whenever someone in the server changes their "playing" status
     */
    @Override
    public void onUserGameUpdate(UserGameUpdateEvent event) {
        super.onUserGameUpdate(event);

        // this isn't in runChecks, so we still remove the role after twitch callback, if the user was given the 
        // probation role while streaming
        if (userHasProbationRole(event.getGuild(), event.getMember()))
            return;

        runChecks(event.getGuild(), event.getMember(), event.getCurrentGame());
    }

    /**
     * Called whenever someone gets added a new role
     * Useful for when someone joins the server already streaming, so game change wont be detected, but
     * if they add region role then we can add the Streaming role
     */
    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        super.onGuildMemberRoleAdd(event);
        GenericUtils.log(event.getUser().getName() + " added role " + event.getRoles().get(0).getName());

        // this isn't in runChecks, so we still remove the role after twitch callback, if the user was given the 
        // probation role while streaming
        if (userHasProbationRole(event.getGuild(), event.getMember()))
            return;

        if (!event.getRoles().get(0).getName().equals(GenericUtils.STREAMING_ROLE_NAME))
            runChecks(event.getGuild(), event.getMember(), event.getMember().getGame());
    }

    private void runChecks(Guild guild, Member member, Game currentGame) {

        if (!userHasAtLeastOneRole(member))
            return;

        // stop if there is no role called GenericUtils.SREAMING_ROLE_NAME
        try {
            streamerRole = guild.getRolesByName(GenericUtils.STREAMING_ROLE_NAME, true).get(0);
        } catch (Exception e) {
            GenericUtils.log("no streamer role found");
            e.printStackTrace();
            return;
        }

        if (!isStreaming(currentGame))
            removeStreamerRole(guild, member);
        else {
            GenericUtils.log(member.getEffectiveName() + " is streaming");

            // Start a new thread to check if is streaming battlerite (because it will call twitch API)
            Thread newThread = new Thread() {
                public void run() {
                    GenericUtils.log(member.getEffectiveName() + " - check if is streaming battlerite on new thread");
                    if (isStreaming(currentGame) && isStreamingBattlerite(currentGame))
                        addStreamerRole(guild, member);
                    else
                        removeStreamerRole(guild, member);
                }
            };

            // wait a few seconds before checking because twitch api takes some time to update their data
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    newThread.start();
                }
            }, TWITCH_CALL_DELAY);

            // Is streaming so call twitch again soon
            scheduleUpdate(guild, member, currentGame);
        }
    }

    /**
     * Checks if the user is streaming on twitch
     */
    private boolean isStreaming(Game currentGame) {
        if (currentGame == null || currentGame.getUrl() == null)
            return false;
        return Game.isValidStreamingUrl(currentGame.getUrl());
    }

    /**
     * Checks if the Game/Category on twitch is Battlerite
     */
    private boolean isStreamingBattlerite(Game currentGame) {
        TwitchInterface api = NetworkUtils.getTwitchRetrofit().create(TwitchInterface.class);
        String twitchUserName = currentGame.getUrl().substring(currentGame.getUrl().lastIndexOf('/') + 1);
        // call twitch api to check the user's stream game/category
        try {
            Response<StreamPOJO> response = api.getStreamByName(twitchUserName).execute();
            GenericUtils.log(response.code() + " " + new Gson().toJson(response.body()));

            // exceeded twitch api quota
            if (response.code() == 429) {
                NetworkUtils.nextTwitchToken();
                return false;
            }

            // no stream found
            if (response.body().getData().isEmpty()) {
                return false;
            }

            return response.body().getData().get(0).getGameId().equals(BattleriteUtils.TWITCH_BATTLERITE_ID);
        } catch (IOException e) {
            GenericUtils.log("failed getting response from twitch");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Add the role to the user
     */
    private void addStreamerRole(Guild guild, Member member) {
        if (!member.getRoles().contains(streamerRole)) {
            GenericUtils.log("add role to " + member);
            guild.getController().addSingleRoleToMember(member, streamerRole).queue();
        }
    }

    /**
     * Check if user has the role and remove it
     */
    private void removeStreamerRole(Guild guild, Member member) {
        if (member.getRoles().contains(streamerRole)) {
            GenericUtils.log("remove role from " + member);
            guild.getController().removeSingleRoleFromMember(member, streamerRole).queue();
        }
    }

    /**
     * Schedule another call to twich to check if the game/category has changed
     */
    private void scheduleUpdate(Guild guild, Member member, Game currentGame) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runChecks(guild, member, currentGame);
            }
        }, RECALL_TWITCH_INTERVAL);
    }

    /**
     * Check if the user as at least one role, which will normally be the region role
     */
    private boolean userHasAtLeastOneRole(Member member) {
        return !member.getRoles().isEmpty();
    }

    /**
     * Check if user has a "probation" role, if yes then dont add the Streamer role
     */
    private boolean userHasProbationRole(Guild guild, Member member) {
        try {
            Role probationRole = guild.getRolesByName(GenericUtils.PROBATION_ROLE_NAME, true).get(0);
            return member.getRoles().contains(probationRole);
        } catch (IndexOutOfBoundsException e) {
            GenericUtils.log("no probation role found");
            e.printStackTrace();
            return false;
        }
    }
}