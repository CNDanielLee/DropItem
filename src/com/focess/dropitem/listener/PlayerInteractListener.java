package com.focess.dropitem.listener;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;

import com.focess.dropitem.Debug;
import com.focess.dropitem.event.DropItemDeathEvent.DeathCause;
import com.focess.dropitem.event.PlayerGottenEvent;
import com.focess.dropitem.item.CraftDropItem;
import com.focess.dropitem.item.EntityDropItem;
import com.focess.dropitem.util.DropItemUtil;
import com.focess.dropitem.util.NMSManager;

public class PlayerInteractListener implements Listener {

    @SuppressWarnings("deprecation")
    private void buildBlock(final Player player, final ItemStack itemStack) {
        try {
            final List<Block> blocks = new ArrayList<>();
            final BlockIterator i = new BlockIterator(player, 4);
            while (i.hasNext())
                blocks.add(i.next());
            final Block temp = this.getTargetBlock(player);
            if (temp.getType().equals(Material.AIR))
                return;
            if ((blocks.indexOf(temp) - 1) < 0)
                return;
            final Block block = blocks.get(blocks.indexOf(temp) - 1);
            if (!block.getType().equals(Material.AIR))
                return;
            boolean flag = false;
            for (final Entity entity : block.getLocation().getWorld().getEntities())
                if (!CraftDropItem.include(entity)) {
                    if (entity instanceof LivingEntity) {
                        final int high = new BigDecimal(((LivingEntity) entity).getEyeHeight() / 0.85F)
                                .setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
                        for (int j = 2; j <= high; j++) {
                            final Location location = entity.getLocation();
                            location.setY((location.getY() + j) - 1);
                            if (this.compareLocation(location, block.getLocation()))
                                return;
                        }
                    }
                    if (this.compareLocation(entity.getLocation(), block.getLocation()))
                        return;
                } else
                    flag = true;
            if (!flag)
                return;
            block.setType(itemStack.getType());
            block.setData((byte) itemStack.getDurability());
            if (!player.getGameMode().equals(GameMode.CREATIVE))
                if (itemStack.getAmount() == 1)
                    itemStack.setType(Material.AIR);
                else
                    itemStack.setAmount(itemStack.getAmount() - 1);
            player.setItemInHand(itemStack);
            player.updateInventory();
            this.playSound(player, block);
        } catch (final Exception e) {
            Debug.debug(e, "Something wrong in building block.");
        }
    }

    private boolean compareLocation(final Location loc, final Location loc2) {
        boolean flag1 = false;
        if (loc.getWorld().getName().equals(loc2.getWorld().getName()))
            if ((loc.getX() <= (loc2.getBlockX() + 1.3)) && (loc.getX() >= (loc2.getBlockX() - 1.3)))
                if (loc.getBlockY() == loc2.getBlockY())
                    if ((loc.getZ() <= (loc2.getBlockZ() + 1.3)) && (loc.getZ() >= (loc2.getBlockZ() - 1.3)))
                        flag1 = true;
        return flag1;
    }

    private Block getTargetBlock(final LivingEntity entity) {
        final int maxLength = 1;
        final int maxDistance = 4;
        final ArrayList<Block> blocks = new ArrayList<>();
        final BlockIterator itr = new BlockIterator(entity, maxDistance);
        while (itr.hasNext()) {
            final Block block = itr.next();
            blocks.add(block);
            if ((maxLength != 0) && (blocks.size() > maxLength))
                blocks.remove(0);
            final Material material = block.getType();
            if (!material.equals(Material.AIR))
                break;
        }
        return blocks.get(0);
    }

    @EventHandler()
    public void onPlayerInteract(final PlayerInteractEvent event) {
        try {
            if (((event.getAction() == Action.RIGHT_CLICK_AIR) || (event.getAction() == Action.RIGHT_CLICK_BLOCK))
                    && (event.getPlayer().getItemInHand() != null)
                    && event.getPlayer().getItemInHand().getType().isBlock())
                this.buildBlock(event.getPlayer(), event.getPlayer().getItemInHand());
        } catch (final Exception e) {
            Debug.debug(e, "Something wrong in calling Event PlayerInteractEvent.");
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        try {
            if (CraftDropItem.include(event.getRightClicked())) {
                event.setCancelled(true);
                if (((DropItemUtil.naturalSpawn() || DropItemUtil.allowedPlayer()
                        || DropItemUtil.checkPlayerPermission(event.getPlayer()))
                        && (event.getPlayer().getItemInHand() == null))
                        || event.getPlayer().getItemInHand().getType().equals(Material.AIR)) {
                    final EntityDropItem dropItem = CraftDropItem.getDropItem(event.getRightClicked());
                    final PlayerGottenEvent e = new PlayerGottenEvent(dropItem.getItemStack(), event.getPlayer());
                    Bukkit.getServer().getPluginManager().callEvent(e);
                    if (e.isCancelled())
                        return;
                    event.getPlayer().setItemInHand(dropItem.getItemStack());
                    CraftDropItem.remove(dropItem, DeathCause.PLAYER_GOTTEN);
                }
            }
        } catch (final Exception e) {
            Debug.debug(e, "Something wrong in calling Event PlayerInteractAtEntityEvent.");
        }
    }

    private void playSound(final Player player, final Block block) {
        try {
            final Object nmsblock = NMSManager.getMethod(NMSManager.getCraftClass("util.CraftMagicNumbers"), "getBlock",
                    new Class[] { Block.class }).invoke(null, block);
            final Field stepSound = NMSManager.getField(NMSManager.getNMSClass("Block"), "stepSound");
            final Object sound = stepSound.get(nmsblock);
            final int version = NMSManager.getVersionInt();
            final Object nmsWorld = NMSManager.getMethod(NMSManager.CraftWorld, "getHandle", new Class[] {})
                    .invoke(block.getWorld(), new Object[] {});
            if (version == 8) {
                final String sound_str = (String) NMSManager
                        .getMethod(sound.getClass(), "getPlaceSound", new Class[] {}).invoke(sound, new Object[] {});
                NMSManager.getMethod(NMSManager.World, "makeSound",
                        new Class[] { double.class, double.class, double.class, String.class, float.class,
                                float.class })
                        .invoke(nmsWorld, block.getLocation().getX(), block.getLocation().getY(),
                                block.getLocation().getZ(), sound_str, 1f, 0.8f);
            } else {
                final Object block_position = NMSManager
                        .getConstructor(NMSManager.getNMSClass("BlockPosition"),
                                new Class[] { double.class, double.class, double.class })
                        .newInstance(block.getLocation().getX(), block.getLocation().getY(),
                                block.getLocation().getZ());
                final Object sound_effect = NMSManager
                        .getMethod(NMSManager.getNMSClass("SoundEffectType"), "e", new Class[] {})
                        .invoke(sound, new Object[] {});
                Object category = null;
                for (final Object e : NMSManager.getNMSClass("SoundCategory").getEnumConstants())
                    if (e.toString().equalsIgnoreCase("BLOCKS"))
                        category = e;
                NMSManager
                        .getMethod(NMSManager.World, "a",
                                new Class[] { NMSManager.getNMSClass("EntityHuman"),
                                        NMSManager.getNMSClass("BlockPosition"), NMSManager.getNMSClass("SoundEffect"),
                                        NMSManager.getNMSClass("SoundCategory"), float.class, float.class })
                        .invoke(nmsWorld, null, block_position, sound_effect, category, 1.0f, 0.8f);
            }
        } catch (final Exception e) {
            Debug.debug(e, "Something wrong in playing sound with Player(" + player.getName() + ")" + "and Block("
                    + block + ")");
        }
    }
}
