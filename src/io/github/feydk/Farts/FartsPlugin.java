package io.github.feydk.Farts;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class FartsPlugin extends JavaPlugin implements Listener
{
	private String potionName = "" + ChatColor.GREEN + "Potion of Farting";
	private PotionType basePotion = PotionType.WEAKNESS;
	protected final PotionEffectType baseEffect = PotionEffectType.WEAKNESS;
	protected final int duration = ((20 * 60) * 3) + 1;
	private FartScheduler scheduler;

	@Override
	public void onEnable()
	{
		reloadConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();

		getServer().getPluginManager().registerEvents(this, this);

		// Register custom recipe for regular Potion of Farting.
		ItemStack potion = getPotion(false, 1);
		ShapedRecipe recipe = new ShapedRecipe(potion);
		recipe.shape(" P ", " R ", " B ");
		recipe.setIngredient('P', Material.POISONOUS_POTATO);
		recipe.setIngredient('R', Material.ROTTEN_FLESH);
		recipe.setIngredient('B', Material.POTION);

		getServer().addRecipe(recipe);

		// Register custom recipe for splash Potion of Farting.
		potion = getPotion(true, 1);
		recipe = new ShapedRecipe(potion);
		recipe.shape(" P ", " R ", "GBG");
		recipe.setIngredient('P', Material.POISONOUS_POTATO);
		recipe.setIngredient('R', Material.ROTTEN_FLESH);
		recipe.setIngredient('B', Material.POTION);
		recipe.setIngredient('G', Material.SULPHUR);

		getServer().addRecipe(recipe);

		scheduler = new FartScheduler(this);
		scheduler.start();
	}

	@Override
	public void onDisable()
	{
		try
		{
			scheduler.stop();
		}
		catch(IllegalStateException ignored)
		{}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[])
	{
		Player player = null;

		if(sender instanceof Player)
			player = (Player)sender;

		if(player == null)
		{
			sender.sendMessage("Player expected");
			return true;
		}

		if(!player.hasPermission("farts.give"))
			return false;

		// /farts give <player> <amount> <splash>

		Player recipient;
		int amount = 1;
		boolean splash = false;

		if(args.length < 1)
		{
			sender.sendMessage("" + ChatColor.RED + "Arguments expected.");
			sender.sendMessage("Syntax: " + ChatColor.YELLOW + "/" + command.getName() + " give [player] [amount] [splash]");
			return true;
		}

		if(args.length < 2)
		{
			sender.sendMessage("" + ChatColor.RED + "You must specify the recipient.");
			return true;
		}

		recipient = getServer().getPlayer(args[1]);

		if(args.length >= 3)
		{
			try
			{
				amount = Integer.parseInt(args[2]);
			}
			catch(NumberFormatException nfe)
			{
				sender.sendMessage("" + ChatColor.RED + "Number expected: " + args[2] + ".");
				return true;
			}
		}

		if(args.length == 4)
		{
			try
			{
				splash = Boolean.parseBoolean(args[3]);
			}
			catch(Exception ex)
			{
				sender.sendMessage("" + ChatColor.RED + "Boolean expected: " + args[3] + ".");
				return true;
			}
		}

		givePotion(recipient, amount, splash);

		return true;
	}

	// Handle splash potions.
	@EventHandler
	public void onPotionSplash(PotionSplashEvent event)
	{
		if(isPotionOfFarting(event.getPotion().getItem()))
		{
			System.out.println("Got hit by Splash Farting!");

			for(LivingEntity e : event.getAffectedEntities())
			{
				if(e instanceof Player)
				{
					applyFarting((Player) e);
				}
			}
		}
		else
		{
			// Someone might use farting, and then continue using weakness potions in an attempt to prolong the effect.
			// Obviously that's sort of cheating, so if the player uses a regular weakness potion, we cancel the farting.
			Potion potion = Potion.fromItemStack(event.getPotion().getItem());

			if(potion.getType() == basePotion)
			{
				for(LivingEntity e : event.getAffectedEntities())
				{
					if(e instanceof Player)
					{
						stopFarting((Player) e);
					}
				}
			}
		}
	}

	// Handle regular/drinkable potions.
	@EventHandler
	public void onPotionDrink(PlayerItemConsumeEvent event)
	{
		if(event.getItem().getType() == Material.POTION)
		{
			if(isPotionOfFarting(event.getItem()))
			{
				System.out.println("Drinking Potion of Farting!");

				applyFarting(event.getPlayer());
			}
			else
			{
				// Someone might use farting, and then continue using weakness potions in an attempt to prolong the effect.
				// Obviously that's sort of cheating, so if the player uses a regular weakness potion, we cancel the farting.
				Potion potion = Potion.fromItemStack(event.getItem());

				if(potion.getType() == basePotion)
				{
					stopFarting(event.getPlayer());
				}
			}
		}
	}

	@EventHandler
	public void onEntityTarget(EntityTargetEvent event)
	{
		handleEntityTargetEvent(event);
	}

	@EventHandler
	public void onEntityTargetLivingEntity(EntityTargetEvent event)
	{
		handleEntityTargetEvent(event);
	}

	// If a farting player attacks a mob, the player takes a severe penalty.
	@EventHandler
	private void onDamageOpponent(EntityDamageByEntityEvent event)
	{
		if(event.getDamager() instanceof Player)
		{
			Player player = (Player)event.getDamager();
			if(scheduler.hasPlayer(player))
			{
				player.damage(4D);
				player.setFoodLevel(player.getFoodLevel() - 2);
			}
		}
		else if(event.getDamager() instanceof Arrow)
		{
			Arrow arrow = (Arrow)event.getDamager();
			ProjectileSource shooter = arrow.getShooter();

			if(shooter instanceof Player)
			{
				Player player = (Player)shooter;
				if(scheduler.hasPlayer(player))
				{
					player.damage(4D);
					player.setFoodLevel(player.getFoodLevel() - 2);
				}
			}
		}
	}

	// Get the potion.
	private ItemStack getPotion(boolean splash, int amount)
	{
		Potion item = new Potion(basePotion);
		item.setSplash(splash);

		ItemStack stack = item.toItemStack(amount);
		PotionEffect effect = new PotionEffect(PotionEffectType.WEAKNESS, duration, 1, true);

		PotionMeta meta = (PotionMeta)stack.getItemMeta();
		meta.setDisplayName(potionName);

		meta.addCustomEffect(effect, true);

		stack.setItemMeta(meta);

		return stack;
	}

	private void givePotion(Player player, int amount, boolean splash)
	{
		player.getInventory().addItem(getPotion(splash, amount));
	}

	// Simple check to detect if potion is a Potion of Farting.
	// Ideally I would like to use hidden metadata for this, but I'm a noob.
	// Instead I just compare the base effect and the name of the potion.
	// This *should* be safe enough because regular users can't give potions custom names with color.
	private boolean isPotionOfFarting(ItemStack item)
	{
		if(item.getType() != Material.POTION)
			return false;

		Potion potion = Potion.fromItemStack(item);

		if(potion.getType() == basePotion)
			return item.getItemMeta().getDisplayName() != null && item.getItemMeta().getDisplayName().equals(potionName);

		return false;
	}

	// Apply the Farting!
	// Play the wolf howl at a lower pitch to indicate this very farty event.
	private void applyFarting(Player player)
	{
		scheduler.addPlayer(player);
		player.getWorld().playSound(player.getLocation(), Sound.WOLF_HOWL, 1F, .3F);
	}

	private void stopFarting(Player player)
	{
		scheduler.removePlayer(player);
	}

	// Handle mobs targeting a player. We ignore mob bosses.
	// If the mob is targeting a player currently farting, we paralyze the mob and register it in the scheduler.
	// If the mob is already paralyzed we just cancel the event.
	private void handleEntityTargetEvent(EntityTargetEvent event)
	{
		if(event.getTarget() instanceof Player && event.getEntity() instanceof Monster)
		{
			//System.out.println(event.getEntity() + " is targeting " + event.getTarget());

			if(event.getEntityType() == EntityType.ENDER_DRAGON || event.getEntityType() == EntityType.WITHER)
				return;

			if(isMobParalyzed(event.getEntity()))
			{
				event.setCancelled(true);
			}
			else
			{
				Player player = (Player) event.getTarget();

				if(scheduler.hasPlayer(player))
				{
					scheduler.addMob((Monster) event.getEntity(), player);
					event.setCancelled(true);
				}
			}
		}
	}

	// Determines if a mob is paralyzed by checking the exact amplifier amount of any slowness
	// inflicted on them. We use such a large unusual number that it should be safe enough to do it this way.
	protected boolean isMobParalyzed(Entity mob)
	{
		if(mob instanceof Monster)
		{
			Collection<PotionEffect> effects = ((Monster)mob).getActivePotionEffects();

			for(PotionEffect effect : effects)
			{
				if(effect.getType().equals(PotionEffectType.SLOW))
				{
					if(effect.getAmplifier() == 123456789)
						return true;
				}
			}
		}

		return false;
	}
}