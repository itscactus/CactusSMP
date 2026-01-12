package its.cactusdev.smp.commands;

import its.cactusdev.smp.menus.MenuHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ClaimCommand implements CommandExecutor {
    private final MenuHandler menu;

    public ClaimCommand(MenuHandler menu) { this.menu = menu; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        menu.openMain(p);
        return true;
    }
}
