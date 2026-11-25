package me.totalfreedom.totalfreedommod.cmd;

import me.totalfreedom.api.cmd.CommandFailException;
import me.totalfreedom.api.cmd.FCommand;
import me.totalfreedom.api.cmd.SourceType;
import me.totalfreedom.api.cmd.annotation.*;
import me.totalfreedom.totalfreedommod.player.PlayerData;
import me.totalfreedom.totalfreedommod.util.FLog;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Command(name = "alts",
        description = "Shows a list of potential alternate accounts for a particular user or IP address.",
        usage = "<command> <ip <IP address> | player <username or UUID>>")
public class Command_alts extends FCommand
{
    @Callback
    @Permission(permission = "tfm.player.alts", source = SourceType.ONLY_IN_GAME)
    public void altsOfSelf(final Player sender)
    {
        altsFromPlayer(sender, getData(sender));
    }

    @Callback
    @Subcommand("ip")
    @Permission(permission = "tfm.player.alts.ips")
    public void altsFromIP(final CommandSender sender,
                           @Resolve(value = "IP") final InetAddress ipAddress)
    {
        msg(sender, "<gray>Searching...");
        CompletableFuture.supplyAsync(() -> getAltsByIP(ipAddress.getHostAddress()))
                .whenComplete((results, ex) ->
                {
                    if (ex != null)
                    {
                        msg(sender, "<red>An error occurred. Please check the logs for more information.");
                        FLog.error("Alt search failed for IP {}", ipAddress.getHostAddress(), ex);
                        return;
                    }

                    if (results.isEmpty())
                    {
                        msg(sender, "<gray>No players were found.");
                    }
                    else
                    {
                        msg(sender, "<gray>The following players were found on this IP address:");
                        msg(sender, "<players>", MessageUtils.joinedList("players",
                                results.stream().map(PlayerData::getUsername).toList(), NamedTextColor.WHITE));
                    }
                });
    }

    @Callback
    @Subcommand("player")
    @Permission(permission = "tfm.player.alts.others")
    public void altsFromPlayer(final CommandSender sender,
                               final PlayerData data)
    {
        final String ip = data.getPlayer() != null ? data.getIp()
                : data.getStoredIp().orElseThrow(() -> new CommandFailException("This player does not have a known IP address."));

        msg(sender, "<gray>Searching...");
        CompletableFuture.supplyAsync(() -> getAltsByIP(ip))
                .whenComplete((results, ex) ->
                {
                    if (ex != null)
                    {
                        msg(sender, "<red>An error occurred. Please check the logs for more information.");
                        FLog.error("Alt search failed for user {}", data.getUuid(), ex);
                        return;
                    }

                    if (results.isEmpty())
                    {
                        msg(sender, "<gray>No players were found.");
                    }
                    else
                    {
                        msg(sender, "<gray>The following players were found with matching IP addresses:");
                        msg(sender, "<players>", MessageUtils.joinedList("players",
                                results.stream().map(PlayerData::getUsername).toList(), NamedTextColor.WHITE));
                    }
                });
    }

    private List<PlayerData> getAltsByIP(final String address)
    {
        final List<PlayerData> candidates = new ArrayList<>();
        //--
        candidates.addAll(server().getOnlinePlayers().stream()
                .filter(player -> Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress().equalsIgnoreCase(address))
                .map(player -> plugin().players().getData(player))
                .toList());
        candidates.addAll(plugin().players().getAllData().stream()
                .filter(data -> !candidates.contains(data))
                .filter(data -> data.getTrustedIps().contains(address)
                        || data.getIp().equalsIgnoreCase(address)
                        || data.getStoredIp().stream().anyMatch(ip -> ip.equalsIgnoreCase(address)))
                .toList());
        //--
        return candidates;
    }
}
