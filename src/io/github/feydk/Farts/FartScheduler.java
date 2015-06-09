package io.github.feydk.Farts;

import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class FartScheduler extends BukkitRunnable
{
	private FartsPlugin plugin;
	private List<Player> players;
	private PotionEffect effect;
	private Map<Monster, ArrayList<Player>> mobs;

	public FartScheduler(FartsPlugin plugin)
	{
		this.plugin = plugin;
		this.players = new ArrayList<Player>();
		this.mobs = new HashMap<Monster, ArrayList<Player>>();

		// This is the effect applied to mobs.
		// The extreme slowness basically makes them not move at all.
		this.effect = new PotionEffect(PotionEffectType.SLOW, plugin.duration, 123456789);
	}

	public void addPlayer(Player player)
	{
		if(!players.contains(player))
			players.add(player);
	}

	public void removePlayer(Player player)
	{
		if(players.contains(player))
			players.remove(player);
	}

	public boolean hasPlayer(Player player)
	{
		return players.contains(player);
	}

	// Add a mob to the list of paralyzed mobs.
	// The reason the player is also registered with the mob is that the same mob can be affected by more than one
	// farting player in their vicinity. We also need to keep track of which players to play the entity effect to.
	public void addMob(Monster mob, Player player)
	{
		if(mobs.get(mob) == null)
		{
			mob.removePotionEffect(effect.getType());
			mob.addPotionEffect(effect, true);

			ArrayList<Player> list = new ArrayList<Player>();
			list.add(player);

			mobs.put(mob, list);
		}
		else
		{
			if(!mobs.get(mob).contains(player))
				mobs.get(mob).add(player);
		}
	}

	public void start()
	{
		// Every ~ 2 seconds should be fine. It isn't all that important; it will only slow down the frequency of
		// player burps and mob's entity effects. However, the task will also check affected players and see if
		// their potion effect has run out. But meh, even if a player should keep the effect for a couple of seconds
		// extra, that's fine.
		runTaskTimer(plugin, 1, 40);
	}

	public void stop()
	{
		for(Map.Entry<Monster, ArrayList<Player>> set : mobs.entrySet())
		{
			set.getKey().removePotionEffect(effect.getType());
		}
	}

	@Override
	public void run()
	{
		if(players.size() == 0 && mobs.size() == 0)
			return;

		//System.out.println("running .. " + players.size());
		List<Player> to_remove = new ArrayList<Player>();
		Random random = new Random();

		for(Player player : players)
		{
			if(player.isOnline())
			{
				if(player.hasPotionEffect(plugin.baseEffect))
				{
					// Every now and then the player will burp, just as a reminder that he currently has a bad stomach.
					if(random.nextInt(1000) >= 750)
					{
						player.getWorld().playSound(player.getLocation(), Sound.BURP, 1F, .9F);
					}
				}
				else
				{
					to_remove.add(player);
				}
			}
		}

		if(to_remove.size() > 0)
		{
			for(Player player : to_remove)
			{
				players.remove(player);
			}
		}

		List<Monster> to_remove2 = new ArrayList<Monster>();

		// Play an entity effect above each mob's head.
		for(Map.Entry<Monster, ArrayList<Player>> set : mobs.entrySet())
		{
			if(!set.getKey().isDead())
			{
				if(plugin.isMobParalyzed(set.getKey()))
				{
					for(Player p : set.getValue())
					{
						p.getWorld().playEffect(set.getKey().getEyeLocation(), Effect.VILLAGER_THUNDERCLOUD, 0);
					}
				}
				else
				{
					to_remove2.add(set.getKey());
				}
			}
			else
			{
				to_remove2.add(set.getKey());
			}
		}

		if(to_remove2.size() > 0)
		{
			for(Monster mob : to_remove2)
			{
				mobs.remove(mob);
			}
		}
	}
}