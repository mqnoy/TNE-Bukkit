package com.github.tnerevival.listeners;

import com.github.tnerevival.TNE;
import com.github.tnerevival.account.Account;
import com.github.tnerevival.core.Message;
import com.github.tnerevival.core.configurations.impl.ObjectConfiguration;
import com.github.tnerevival.core.shops.Shop;
import com.github.tnerevival.core.signs.ShopSign;
import com.github.tnerevival.core.signs.SignType;
import com.github.tnerevival.core.signs.TNESign;
import com.github.tnerevival.core.transaction.TransactionType;
import com.github.tnerevival.serializable.SerializableLocation;
import com.github.tnerevival.utils.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class InteractionListener implements Listener {
	
	TNE plugin;
	
	public InteractionListener(TNE plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent event) {
		if(TNE.configurations.getBoolean("Objects.Commands.Enabled", "objects")) {
			
			ObjectConfiguration configuration = TNE.configurations.getObjectConfiguration();
			
			Player player = event.getPlayer();
			String command = event.getMessage().substring(1);
			String[] commandSplit = command.split(" ");
			String commandName = commandSplit[0];
			String commandFirstArg = commandSplit[0] + ((commandSplit.length > 1) ? " " + commandSplit[1] : "");
			double cost = configuration.getCommandCost(commandName.toLowerCase(), (commandSplit.length > 1) ? new String[] { commandSplit[1].toLowerCase() } : new String[0]);
			
			Message commandCost = new Message("Messages.Command.Charge");
			commandCost.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(event.getPlayer()), AccountUtils.round(cost)));
			commandCost.addVariable("$command", commandFirstArg);
			
			if(cost > 0.0) {
			  String message = "";
        Account acc = AccountUtils.getAccount(MISCUtils.getID(player));
        if(TNE.instance.manager.enabled(MISCUtils.getID(player), MISCUtils.getWorld(player))) {
          if(!TNE.instance.manager.confirmed(MISCUtils.getID(player), MISCUtils.getWorld(player))) {
            if (acc.getPin().equalsIgnoreCase("TNENOSTRINGVALUE"))
              message = "Messages.Account.Set";
            else if (!acc.getPin().equalsIgnoreCase("TNENOSTRINGVALUE"))
              message = "Messages.Account.Confirm";
          }
        }

        if(!message.equals("")) {
          event.setCancelled(true);
          player.sendMessage(new Message(message).translate());
          return;
        }
				event.setCancelled(true);
				
				boolean paid = false;
				
				if(acc.hasCredit(commandFirstArg)) {
					acc.removeCredit(commandFirstArg);
				} else {
					if(TNE.instance.api.fundsHas(player, player.getWorld().getName(), cost)) {
						TNE.instance.api.fundsRemove(player, player.getWorld().getName(), cost);
						paid = true;
					}
				}
				
				if(paid) {
					if(!player.performCommand(command)) {
						acc.addCredit(commandFirstArg);
						return;
					}		
					
					player.sendMessage(commandCost.translate());
				}
				return;
			}
			
			if(TNE.configurations.getBoolean("Objects.Commands.ZeroMessage", "objects")) {
				player.sendMessage(commandCost.translate());
			}
		}
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		String name = MaterialUtils.formatMaterialNameWithoutSpace(event.getBlock().getType()).toLowerCase();

    if(event.getBlock().getType().equals(Material.WALL_SIGN) || event.getBlock().getType().equals(Material.SIGN_POST)) {
      if(SignUtils.validSign(event.getBlock().getLocation())) {
        SerializableLocation location = new SerializableLocation(event.getBlock().getLocation());
        TNESign sign = SignUtils.getSign(location);

				MISCUtils.debug(sign.toString() + "");
        if(!sign.onDestroy(event.getPlayer())) {
          event.setCancelled(true);
        } else {
          SignUtils.removeSign(location);
        }
        return;
      }
    }

		if(TNE.configurations.getMaterialsConfiguration().containsBlock(name)) {
			Player player = event.getPlayer();
			Double cost = TNE.configurations.getMaterialsConfiguration().getBlock(name).getMine();
			

			String message = "Messages.Objects.MiningCharged";
			if(cost > 0.0) {
				if(AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_INQUIRY, MISCUtils.getWorld(player))) {
					AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_REMOVE, MISCUtils.getWorld(player));
				} else {
					event.setCancelled(true);
					Message insufficient = new Message("Messages.Money.Insufficient");
					insufficient.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
					player.sendMessage(insufficient.translate());
					return;
				}
			} else {
        AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_GIVE, MISCUtils.getWorld(player));
				message = "Messages.Objects.MiningPaid";
			}
			
			if(cost > 0.0 || cost < 0.0 || cost == 0.0 && TNE.configurations.getBoolean("Materials.Blocks.ZeroMessage")) {
				
				Message m = new Message(message);
				m.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
				m.addVariable("$name", name);
				player.sendMessage(m.translate());
			}
		}
	}

	@EventHandler
	public void onPlace(BlockPlaceEvent event) {
		String name = MaterialUtils.formatMaterialNameWithoutSpace(event.getBlock().getType()).toLowerCase();

    if(event.getBlock().getType().equals(Material.WALL_SIGN) || event.getBlock().getType().equals(Material.SIGN_POST)) {
      if(SignUtils.validSign(event.getBlock().getLocation())) {
      	MISCUtils.debug("Sign placed");
        return;
      }
    }

		if(TNE.configurations.getMaterialsConfiguration().containsBlock(name)) {
			Player player = event.getPlayer();
			Double cost = TNE.configurations.getMaterialsConfiguration().getBlock(name).getPlace();
			

			String message = "Messages.Objects.PlacingCharged";
			if(cost > 0.0) {
				if(AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_INQUIRY, MISCUtils.getWorld(player))) {
          AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_REMOVE, MISCUtils.getWorld(player));
				} else {
					event.setCancelled(true);
					Message insufficient = new Message("Messages.Money.Insufficient");
					insufficient.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
					player.sendMessage(insufficient.translate());
					return;
				}
			} else {
        AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_GIVE, MISCUtils.getWorld(player));
				message = "Messages.Objects.PlacingPaid";
			}
			
			if(cost > 0.0 || cost < 0.0 || cost == 0.0 && TNE.configurations.getBoolean("Materials.Blocks.ZeroMessage")) {
				
				Message m = new Message(message);
				m.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
				m.addVariable("$name", name);
				player.sendMessage(m.translate());
			}
		}
	}
	
	@EventHandler
	public void onSmelt(FurnaceSmeltEvent event) {
		if(event.getResult() != null && !event.getResult().getType().equals(Material.AIR)) {
			String name = MaterialUtils.formatMaterialNameWithoutSpace(event.getSource().getType()).toLowerCase();
			Double cost = 0.0;
			if(event.getBlock().getState() instanceof Furnace) {
				Furnace f = (Furnace)event.getBlock().getState();
				
				int amount = (f.getInventory().getResult() != null) ? f.getInventory().getResult().getAmount() : 1;
				
				if(TNE.configurations.getMaterialsConfiguration().containsItem(name)) {
					cost = TNE.configurations.getMaterialsConfiguration().getItem(name).getSmelt() * amount;
				} else if(TNE.configurations.getMaterialsConfiguration().containsBlock(name)) {
					cost = TNE.configurations.getMaterialsConfiguration().getBlock(name).getSmelt() * amount;
				} else {
					return;
				}
				
				List<String> lore = new ArrayList<String>();
				lore.add(ChatColor.WHITE + "Smelting Cost: " + ChatColor.GOLD + cost);
				
				ItemStack result = event.getResult();
				ItemMeta meta = result.getItemMeta();
				meta.setLore(lore);
				
				result.setItemMeta(meta);
				event.setResult(result);
			}
		}
	}

	@EventHandler
	public void onEnchant(EnchantItemEvent event) {
		if(event.getItem() != null && !event.getItem().getType().equals(Material.AIR)) {
			
			ItemStack result = event.getItem();
			String name = MaterialUtils.formatMaterialNameWithoutSpace(result.getType()).toLowerCase();
			Double cost = 0.0;
			
			if(TNE.configurations.getMaterialsConfiguration().containsItem(name)) {
				cost = TNE.configurations.getMaterialsConfiguration().getItem(name).getCrafting();
			} else {
				return;
			}
			
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.WHITE + "Enchanting Cost: " + ChatColor.GOLD + cost);
			
			ItemMeta meta = result.getItemMeta();
			meta.setLore(lore);
			
			for(Enchantment e : event.getEnchantsToAdd().keySet()) {
				meta.addEnchant(e, event.getEnchantsToAdd().get(e), false);
			}
			
			result.setItemMeta(meta);
			event.getInventory().setItem(0, result);
		}
	}
	
	@EventHandler
	public void onPreCraft(PrepareItemCraftEvent event) {
		if(event.getInventory().getResult() != null) {
			String name = MaterialUtils.formatMaterialNameWithoutSpace(event.getInventory().getResult().getType()).toLowerCase();
			Double cost = 0.0;
			
			if(TNE.configurations.getMaterialsConfiguration().containsItem(name)) {
				cost = TNE.configurations.getMaterialsConfiguration().getItem(name).getCrafting();
			} else if(TNE.configurations.getMaterialsConfiguration().containsBlock(name)) {
				cost = TNE.configurations.getMaterialsConfiguration().getBlock(name).getCrafting();
			} else {
				return;
			}
			
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.WHITE + "Crafting Cost: " + ChatColor.GOLD + cost);
			
			ItemStack result = event.getInventory().getResult();
			ItemMeta meta = result.getItemMeta();
			meta.setLore(lore);
			result.setItemMeta(meta);
			event.getInventory().setResult(result);
		}
	}
	
	@EventHandler
	public void onCraft(CraftItemEvent event) {
		
		String name = MaterialUtils.formatMaterialNameWithoutSpace(event.getInventory().getResult().getType()).toLowerCase();
		Double cost = 0.0;
		boolean item = false;
		
		if(TNE.configurations.getMaterialsConfiguration().containsItem(name)) {
			cost = TNE.configurations.getMaterialsConfiguration().getItem(name).getCrafting();
			item = true;
		} else if(TNE.configurations.getMaterialsConfiguration().containsBlock(name)) {
			cost = TNE.configurations.getMaterialsConfiguration().getBlock(name).getCrafting();
		}
		
		ItemStack result = event.getInventory().getResult();
		ItemMeta meta = result.getItemMeta();
		meta.setLore(new ArrayList<String>());
		result.setItemMeta(meta);
		
		Player player = (Player)event.getWhoClicked();
		String message = "Messages.Objects.CraftingCharged";
		if(cost > 0.0) {
			if(AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_INQUIRY, MISCUtils.getWorld(player))) {
        AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_REMOVE, MISCUtils.getWorld(player));
			} else {
				event.setCancelled(true);
				Message insufficient = new Message("Messages.Money.Insufficient");
				insufficient.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
				player.sendMessage(insufficient.translate());
				return;
			}
		} else {
      AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_GIVE, MISCUtils.getWorld(player));
			message = "Messages.Objects.CraftingPaid";
		}
		
		if(cost > 0.0 || cost < 0.0  || cost == 0.0 && item && TNE.configurations.getBoolean("Materials.Items.ZeroMessage") || cost == 0.0 && !item && TNE.configurations.getBoolean("Materials.Blocks.ZeroMessage")) {
			String newName = (result.getAmount() > 1)? name + "'s" : name;
			
			Message m = new Message(message);
			m.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
			m.addVariable("$stack_size", result.getAmount() + "");
			m.addVariable("$item", newName);
			player.sendMessage(m.translate());
		}
		
		event.getInventory().setResult(result);
	}
	
	@EventHandler
	public void onChange(SignChangeEvent event) {
	  if(event.getLine(0).contains("tne:")) {
      String[] match = event.getLine(0).substring(1, event.getLine(0).length() - 1).split(":");

      if (match.length > 1) {
        MISCUtils.debug(match[0] + " type: " + match[1]);
        SignType type = SignType.fromName(match[1]);

        TNESign sign = SignUtils.instance(type.getName(), MISCUtils.getID(event.getPlayer()));
        sign.setLocation(new SerializableLocation(event.getBlock().getLocation()));

        if(sign instanceof ShopSign) {
        	if(!Shop.exists(event.getLine(1), event.getBlock().getWorld().getName())) {
        		event.setCancelled(true);
            return;
					}
          ((ShopSign) sign).setName(event.getLine(1), event.getBlock().getWorld().getName());
        }

        if (!sign.onCreate(event.getPlayer())) {
          event.setCancelled(true);
        } else {
        	Double place = sign.getType().place(MISCUtils.getWorld(event.getPlayer()), MISCUtils.getID(event.getPlayer()).toString());
          MISCUtils.debug("Interaction " + place);
          MISCUtils.debug("Interaction " + sign.getType().name());
          if(place != null && place > 0.0) {
            AccountUtils.transaction(MISCUtils.getID(event.getPlayer()).toString(), null, place, TransactionType.MONEY_REMOVE, MISCUtils.getWorld(event.getPlayer()));
            Message charged = new Message("Messages.Objects.SignPlace");
            charged.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(event.getPlayer()), place));
            event.getPlayer().sendMessage(charged.translate());
          }
          TNE.instance.manager.signs.put(sign.getLocation(), sign);
        }
      }
    }
	}
	
	@EventHandler
	public void onInteractWithEntity(PlayerInteractEntityEvent event) {
		Entity entity = event.getRightClicked();
		Player player = event.getPlayer();
		String world = TNE.instance.defaultWorld;
		
		if(MISCUtils.multiWorld()) {
			world = player.getWorld().getName();
		}

		if(entity instanceof Villager) {
			Villager villager = (Villager)entity;

      if(player.getInventory().getItemInMainHand().getType().equals(Material.NAME_TAG) && !player.hasPermission("tne.bypass.nametag")) {
        event.setCancelled(true);
        player.sendMessage(new Message("Messages.Mob.NPCTag").translate());
      }

			if(villager.getCustomName() != null && villager.getCustomName().equalsIgnoreCase("banker")) {
				event.setCancelled(true);
				if(player.hasPermission("tne.bank.use")) {
					if(BankUtils.enabled(world, MISCUtils.getID(player).toString())) {
						if(BankUtils.npc(world)) {
							if(BankUtils.hasBank(MISCUtils.getID(player))) {
								Inventory bankInventory = BankUtils.getBankInventory(MISCUtils.getID(player));
								player.openInventory(bankInventory);
							} else {
								player.sendMessage(new Message("Messages.Bank.None").translate());
							}
						} else {
							player.sendMessage(new Message("Messages.Bank.NoNPC").translate());
						}
					} else {
						player.sendMessage(new Message("Messages.Bank.Disabled").translate());
					}
				} else {
					player.sendMessage(new Message("Messages.General.NoPerm").translate());
				}
			}
		}
	}

  @EventHandler(priority = EventPriority.HIGH)
  public void onRightClick(PlayerInteractEvent event) {
    Action action = event.getAction();
    Player player = event.getPlayer();
    String world = player.getWorld().getName();
    Block block = event.getClickedBlock();
    MISCUtils.debug(TNE.instance.manager.signs.size() + "");

    if (action.equals(Action.RIGHT_CLICK_AIR) || action.equals(Action.RIGHT_CLICK_BLOCK)) {
      if(action.equals(Action.RIGHT_CLICK_BLOCK) && block.getType().equals(Material.WALL_SIGN) || action.equals(Action.RIGHT_CLICK_BLOCK) && block.getType().equals(Material.SIGN_POST)) {
        if(SignUtils.validSign(block.getLocation())) {
          SerializableLocation location = new SerializableLocation(block.getLocation());
          Sign b = (Sign)block.getState();
          TNESign sign = SignUtils.getSign(location);

          for(TNESign s : TNE.instance.manager.signs.values()) {
            MISCUtils.debug(s.getLocation().toString() + ";" + s.getType() + ";" + s.getOwner());
          }
          MISCUtils.debug(SignUtils.validSign(block.getLocation()) + "");
          MISCUtils.debug(SignUtils.getSign(location).toString() + "");
          if(sign == null) {
            MISCUtils.debug("Sign instance is null");
          }

          if(sign instanceof ShopSign) {
            if(!((ShopSign)sign).onRightClick(player, b.getLine(1), b.getWorld().getName())) {
              event.setCancelled(true);
            }
          } else{
            if (!sign.onRightClick(player)) {
              event.setCancelled(true);
            }
          }
          if(!event.isCancelled()) {
            Double use = sign.getType().use(MISCUtils.getWorld(event.getPlayer()), MISCUtils.getID(event.getPlayer()).toString());
            AccountUtils.transaction(MISCUtils.getID(event.getPlayer()).toString(), null, use, TransactionType.MONEY_REMOVE, MISCUtils.getWorld(event.getPlayer()));
            Message charged = new Message("Messages.Objects.SignUse");
            charged.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(event.getPlayer()), use));
            event.getPlayer().sendMessage(charged.translate());
          }
        }
      } else {
        Double cost = 0.0;
        String name = MaterialUtils.formatMaterialNameWithoutSpace(event.getMaterial()).toLowerCase();
        if(event.getItem() != null) {
          if(TNE.configurations.getMaterialsConfiguration().containsItem(name)) {
            cost = TNE.configurations.getMaterialsConfiguration().getItem(name).getUse();
          }

          if(TNE.configurations.getMaterialsConfiguration().containsItem(name)) {
            String message = "Messages.Objects.ItemUseCharged";
            if (cost > 0.0) {
              if (AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_INQUIRY, MISCUtils.getWorld(player))) {
                AccountUtils.transaction(MISCUtils.getID(player).toString(), null, cost, TransactionType.MONEY_REMOVE, MISCUtils.getWorld(player));
              } else {
                event.setCancelled(true);
                Message insufficient = new Message("Messages.Money.Insufficient");
                insufficient.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
                player.sendMessage(insufficient.translate());
                return;
              }
            }

            if(cost > 0.0 || cost < 0.0  || cost == 0.0 && TNE.configurations.getBoolean("Materials.Items.ZeroMessage")) {

              Message m = new Message(message);
              m.addVariable("$amount", MISCUtils.formatBalance(MISCUtils.getWorld(player), AccountUtils.round(cost)));
              m.addVariable("$item", name);
              player.sendMessage(m.translate());
            }
          }
        }
			}
    }
  }

	@EventHandler(priority = EventPriority.HIGH)
	public void onEntityDeath(EntityDeathEvent event) {
		LivingEntity entity = event.getEntity();
		
		if(entity.getKiller() != null) {
			Player killer = entity.getKiller();
			String mob = entity.getCustomName();
			Double reward = TNE.configurations.mobReward("Default");
			String messageNode = "Messages.Mob.Killed";
			
			if((TNE.configurations.getBoolean("Mobs.Enabled", "mob"))) {
				switch(entity.getType()) {
					case BAT:
						mob = "Bat";
						break;
					case BLAZE:
						mob = "Blaze";
						break;
					case CAVE_SPIDER:
						mob = "CaveSpider";
						break;
					case CHICKEN:
						mob = "Chicken";
						break;
					case COW:
						mob = "Cow";
						break;
					case CREEPER:
						mob = "Creeper";
						break;
					case ENDER_DRAGON:
						mob = "EnderDragon";
						break;
					case ENDERMAN:
						mob = "Enderman";
						break;
					case ENDERMITE:
						mob = "Endermite";
						break;
					case GHAST:
						mob = "Ghast";
						break;
					case GIANT:
						mob = "Giant";
						break;
					case GUARDIAN:
						Guardian guard = (Guardian)entity;
						if(guard.isElder()) {
							mob = "GuardianElder";
							break;
						}
						mob = "Guardian";
						break;
					case HORSE:
						mob = "Horse";
						break;
					case IRON_GOLEM:
						mob = "IronGolem";
						break;
					case MAGMA_CUBE:
						mob = "MagmaCube";
						break;
					case MUSHROOM_COW:
						mob = "Mooshroom";
						break;
					case OCELOT:
						mob = "Ocelot";
						break;
					case PIG:
						mob = "Pig";
						break;
					case PIG_ZOMBIE:
						mob = "ZombiePigman";
						break;
          case PLAYER:
            mob = "Player";
            Player p = (Player)entity;
            if(TNE.configurations.mobEnabled(p.getDisplayName())) {
              mob = p.getDisplayName();
            }
            break;
					case POLAR_BEAR:
						mob = "PolarBear";
						break;
					case RABBIT:
						Rabbit rab = (Rabbit)entity;
						if(rab.getType().equals(Rabbit.Type.THE_KILLER_BUNNY)) {
							mob = "RabbitKiller";
						}
						mob = "Rabbit";
						break;
					case SHEEP:
						mob = "Sheep";
						break;
					case SHULKER:
						mob = "Shulker";
						break;
					case SILVERFISH:
						mob = "Silverfish";
						break;
					case SKELETON:
						Skeleton skelly = (Skeleton)entity;
						if(skelly.getSkeletonType().equals(SkeletonType.WITHER)) {
							mob = "WitherSkeleton";
							break;
						}  else if(MISCUtils.isOneTen() && skelly.getSkeletonType().equals(SkeletonType.STRAY)) {
							mob = "Stray";
							break;
						}
						mob = "Skeleton";
						break;
					case SLIME:
						mob = "Slime";
						break;
					case SNOWMAN:
						mob = "SnowGolem";
						break;
					case SPIDER:
						mob = "Spider";
						break;
					case SQUID:
						mob = "Squid";
						break;
					case VILLAGER:
						mob = "Villager";
						break;
					case WITCH:
						mob = "Witch";
						break;
					case WITHER:
						mob = "Wither";
						break;
					case WOLF:
						mob = "Wolf";
						break;
					case ZOMBIE:
						Zombie zombles = (Zombie)entity;
						if(zombles.isVillager()) {
							mob = "ZombieVillager";
							break;
						}
						if(MISCUtils.isOneTen() && zombles.getVillagerProfession().equals(Villager.Profession.HUSK)) {
							mob = "Husk";
							break;
						}
						mob = "Zombie";
						break;
					default:
						mob = "Default";
						break;
				}
				mob = (mob.equalsIgnoreCase("Default")) ? (entity.getCustomName() != null) ? entity.getCustomName() : mob : mob;
				Character firstChar = mob.charAt(0);
				reward = TNE.configurations.mobReward(mob);
				messageNode = (firstChar == 'a' || firstChar == 'e' || firstChar == 'i' || firstChar == 'o' || firstChar == 'u') ? "Messages.Mob.KilledVowel" : "Messages.Mob.Killed";
				if(TNE.configurations.mobEnabled(mob)) {
          AccountUtils.transaction(MISCUtils.getID(killer).toString(), null, reward, TransactionType.MONEY_GIVE, MISCUtils.getWorld(killer));
					Message mobKilled = new Message(messageNode);
					mobKilled.addVariable("$mob", mob);
					mobKilled.addVariable("$reward", MISCUtils.formatBalance(MISCUtils.getWorld(killer), reward));
					killer.sendMessage(mobKilled.translate());
				}
			}
		}
	}
}