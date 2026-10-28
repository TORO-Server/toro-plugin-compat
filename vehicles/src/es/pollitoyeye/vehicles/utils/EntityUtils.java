package es.pollitoyeye.vehicles.utils;

import es.pollitoyeye.vehicles.VehiclesMain;
import es.pollitoyeye.vehicles.beans.BoundingBoxData;
import es.pollitoyeye.vehicles.interfaces.VehicleSubType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Compatibility layer for Paper 26.x (Mojang-mapped server, no versioned CraftBukkit package).
 *
 * <p>Replacement for the original reflection layer, which resolved Spigot-obfuscated member names
 * per Minecraft version (1.14 - 1.20.4). The public API of this class is unchanged, so the rest of
 * the plugin does not need to be modified. All NMS access uses the Mojang names that the Paper
 * server uses at runtime.
 */
public class EntityUtils {
   // Kept for source compatibility with callers; Paper 26.x only.
   public static int minecraftVersion = 26;
   public static int minecraftSubVersion = 3;
   public static boolean newHandRender = true;

   private static final double seatOffset = -0.25;
   private static final float INPUT_SCALE = 0.98F;

   private static NamespacedKey modelGroupKey;

   private static Class<?> craftEntityClass;
   private static Class<?> craftPlayerClass;
   private static Class<?> craftWorldClass;
   private static Method craftEntityGetHandle;
   private static Method craftPlayerGetHandle;
   private static Method craftWorldGetHandle;

   // net.minecraft.world.entity.Entity
   public static Class<?> MinecraftEntityClass;
   private static Method entityGetYRot;
   private static Method entitySetYRot;
   private static Method entitySnapTo;
   private static Method entitySetBoundingBox;
   private static Method entityGetId;
   private static Method entitySetInvisible;
   private static Method entityGetEntityData;
   private static Method entityBlockPosition;
   private static Method entityOnGround;
   private static Field entityNoPhysics;
   private static Field entityDimensions;
   private static Field entityVehicle;
   private static Method entityGetBukkitEntity;

   // AABB / EntityDimensions
   private static Constructor<?> aabbConstructor;
   private static Method dimensionsFixed;

   // ArmorStand
   private static Class<?> armorStandClass;
   private static Constructor<?> armorStandConstructor;
   private static Field armorStandDisabledSlots;
   private static Method armorStandSetSmall;
   private static Method armorStandSetShowArms;
   private static Method armorStandGetPose;
   private static Method armorStandSetPose;
   private static Method livingGetItemBySlot;
   private static Object armorStandEntityType;

   // Creeper (used as an invisible camera entity)
   private static Constructor<?> creeperConstructor;
   private static Object creeperEntityType;

   // Packets
   private static Field playerConnectionField;
   private static Method connectionSend;
   private static Constructor<?> cameraPacketConstructor;
   private static Constructor<?> removeEntitiesPacketConstructor;
   private static Constructor<?> addEntityPacketConstructor;
   private static Constructor<?> entityDataPacketConstructor;
   private static Method synchedGetNonDefaultValues;
   private static Constructor<?> equipmentPacketConstructor;
   private static Method teleportPacketFactory;
   private static Method positionMoveRotationOf;
   private static Constructor<?> positionMoveRotationConstructor;
   private static Constructor<?> vec3Constructor;
   private static Constructor<?> headRotationPacketConstructor;
   private static Constructor<?> pairConstructor;
   private static Method pairGetFirst;
   private static Method pairGetSecond;
   private static Object[] equipmentSlots;
   private static Method craftItemStackAsNMSCopy;

   private static boolean initialised;

   public static void initReflection(String[] unused) {
      try {
         modelGroupKey = new NamespacedKey(VehiclesMain.getPlugin(), "MODEL_GROUP");
         newHandRender = true;

         craftEntityClass = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
         craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
         craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
         craftEntityGetHandle = craftEntityClass.getMethod("getHandle");
         craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");
         craftWorldGetHandle = craftWorldClass.getMethod("getHandle");

         MinecraftEntityClass = Class.forName("net.minecraft.world.entity.Entity");
         entityGetYRot = MinecraftEntityClass.getMethod("getYRot");
         entitySetYRot = MinecraftEntityClass.getMethod("setYRot", float.class);
         entitySnapTo = MinecraftEntityClass.getMethod("snapTo", double.class, double.class, double.class, float.class, float.class);
         Class<?> aabbClass = Class.forName("net.minecraft.world.phys.AABB");
         entitySetBoundingBox = MinecraftEntityClass.getMethod("setBoundingBox", aabbClass);
         entityGetId = MinecraftEntityClass.getMethod("getId");
         entitySetInvisible = MinecraftEntityClass.getMethod("setInvisible", boolean.class);
         entityGetEntityData = MinecraftEntityClass.getMethod("getEntityData");
         entityBlockPosition = MinecraftEntityClass.getMethod("blockPosition");
         entityOnGround = MinecraftEntityClass.getMethod("onGround");
         entityNoPhysics = MinecraftEntityClass.getField("noPhysics");
         entityGetBukkitEntity = MinecraftEntityClass.getMethod("getBukkitEntity");
         entityDimensions = MinecraftEntityClass.getDeclaredField("dimensions");
         entityDimensions.setAccessible(true);
         entityVehicle = MinecraftEntityClass.getDeclaredField("vehicle");
         entityVehicle.setAccessible(true);

         aabbConstructor = aabbClass.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);
         dimensionsFixed = Class.forName("net.minecraft.world.entity.EntityDimensions").getMethod("fixed", float.class, float.class);

         Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
         Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
         armorStandClass = Class.forName("net.minecraft.world.entity.decoration.ArmorStand");
         Class<?> entityTypesClass = Class.forName("net.minecraft.world.entity.EntityTypes");
         armorStandEntityType = entityTypesClass.getField("ARMOR_STAND").get(null);
         armorStandConstructor = armorStandClass.getConstructor(entityTypeClass, levelClass);
         armorStandDisabledSlots = armorStandClass.getField("disabledSlots");
         armorStandSetSmall = armorStandClass.getMethod("setSmall", boolean.class);
         armorStandSetShowArms = armorStandClass.getMethod("setShowArms", boolean.class);
         armorStandGetPose = armorStandClass.getMethod("getArmorStandPose");
         armorStandSetPose = armorStandClass.getMethod("setArmorStandPose", armorStandGetPose.getReturnType());

         Class<?> creeperClass = Class.forName("net.minecraft.world.entity.monster.Creeper");
         creeperEntityType = entityTypesClass.getField("CREEPER").get(null);
         creeperConstructor = creeperClass.getConstructor(entityTypeClass, levelClass);

         Class<?> equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
         equipmentSlots = equipmentSlotClass.getEnumConstants();
         livingGetItemBySlot = Class.forName("net.minecraft.world.entity.LivingEntity").getMethod("getItemBySlot", equipmentSlotClass);
         Class<?> nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
         craftItemStackAsNMSCopy = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);

         Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
         pairConstructor = pairClass.getConstructor(Object.class, Object.class);
         pairGetFirst = pairClass.getMethod("getFirst");
         pairGetSecond = pairClass.getMethod("getSecond");

         Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
         playerConnectionField = serverPlayerClass.getField("connection");
         Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
         connectionSend = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl").getMethod("send", packetClass);

         String protocol = "net.minecraft.network.protocol.game.";
         cameraPacketConstructor = Class.forName(protocol + "ClientboundSetCameraPacket").getConstructor(MinecraftEntityClass);
         removeEntitiesPacketConstructor = Class.forName(protocol + "ClientboundRemoveEntitiesPacket").getConstructor(int[].class);
         addEntityPacketConstructor = Class.forName(protocol + "ClientboundAddEntityPacket")
            .getConstructor(MinecraftEntityClass, int.class, Class.forName("net.minecraft.core.BlockPos"));
         Class<?> synchedClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
         synchedGetNonDefaultValues = synchedClass.getMethod("getNonDefaultValues");
         entityDataPacketConstructor = Class.forName(protocol + "ClientboundSetEntityDataPacket")
            .getConstructor(int.class, List.class);
         equipmentPacketConstructor = Class.forName(protocol + "ClientboundSetEquipmentPacket").getConstructor(int.class, List.class);
         Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
         vec3Constructor = vec3Class.getConstructor(double.class, double.class, double.class);
         Class<?> pmrClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
         positionMoveRotationOf = pmrClass.getMethod("of", MinecraftEntityClass);
         positionMoveRotationConstructor = pmrClass.getConstructor(vec3Class, vec3Class, float.class, float.class);
         teleportPacketFactory = Class.forName(protocol + "ClientboundTeleportEntityPacket")
            .getMethod("teleport", int.class, pmrClass, Set.class, boolean.class);
         headRotationPacketConstructor = Class.forName(protocol + "ClientboundRotateHeadPacket").getConstructor(MinecraftEntityClass, byte.class);

         initialised = true;
      } catch (Throwable t) {
         initialised = false;
         es.pollitoyeye.vehicles.listeners.EventListener.joinMessage = org.bukkit.ChatColor.AQUA
            + "[Vehicles]"
            + org.bukkit.ChatColor.GRAY
            + " It seems the plugin is not compatible \nwith your version ("
            + Bukkit.getVersion()
            + "), the NMS compatibility layer failed to initialise.";
         VehiclesMain.getPlugin().getLogger().log(Level.SEVERE, "Failed to initialise the Paper 26.x compatibility layer", t);
      }
   }

   private static Object handle(Entity entity) throws Exception {
      return craftEntityGetHandle.invoke(entity);
   }

   private static Object playerHandle(Player player) throws Exception {
      return craftPlayerGetHandle.invoke(player);
   }

   private static void send(Player player, Object packet) throws Exception {
      Object connection = playerConnectionField.get(playerHandle(player));
      connectionSend.invoke(connection, packet);
   }

   /** Returns {strafe * 6, forward}; strafe is positive to the left, forward positive when moving forward. */
   public static float[] sidesFront(Player player) {
      float[] result = new float[2];
      try {
         Input input = player.getCurrentInput();
         float strafe = (input.isLeft() ? 1.0F : 0.0F) - (input.isRight() ? 1.0F : 0.0F);
         float forward = (input.isForward() ? 1.0F : 0.0F) - (input.isBackward() ? 1.0F : 0.0F);
         result[0] = strafe * INPUT_SCALE * 6.0F;
         result[1] = forward * INPUT_SCALE;
      } catch (Throwable t) {
         t.printStackTrace();
      }
      return result;
   }

   public static boolean isSpacePressed(Player player) {
      try {
         return player.getCurrentInput().isJump();
      } catch (Throwable t) {
         return false;
      }
   }

   public static void setYaw(Entity entity, float yaw) {
      if (entity != null) {
         try {
            entitySetYRot.invoke(handle(entity), yaw % 360.0F);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static float getYaw(Entity entity) {
      if (entity == null) {
         return 0.0F;
      }
      try {
         return (Float)entityGetYRot.invoke(handle(entity));
      } catch (Exception e) {
         e.printStackTrace();
         return 0.0F;
      }
   }

   public static double getSeatOffset() {
      return seatOffset;
   }

   public static void removeSlots(ArmorStand stand) {
      try {
         armorStandDisabledSlots.setInt(handle(stand), 2096896);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void setGravity(ArmorStand stand, boolean gravity) {
      stand.setGravity(gravity);
      try {
         entityNoPhysics.setBoolean(handle(stand), !gravity);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void setCanTick(ArmorStand stand) {
      if (stand != null) {
         try {
            stand.setCanTick(true);
         } catch (Throwable ignored) {
         }
      }
   }

   public static void setLocation(Entity entity, Location location) {
      try {
         entitySnapTo.invoke(handle(entity), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void checkChunks(Location location, int distance) {
      for (int x = 0; x < 3; x++) {
         for (int z = 0; z < 3; z++) {
            Chunk chunk = location.clone().add((x - 1) * distance, 0.0, (z - 1) * distance).getChunk();
            if (!chunk.isLoaded()) {
               chunk.load();
            }
         }
      }
   }

   public static void saveUUIDData(ArmorStand stand) {
      ItemStack item = new ItemStack(Material.ANVIL);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(stand.getUniqueId().toString());
      item.setItemMeta(meta);
      stand.setChestplate(item);
   }

   public static boolean checkSavedUUID(Entity entity, String uuid) {
      if (!(entity instanceof ArmorStand) || uuid == null) {
         return false;
      }
      ItemStack item = ((ArmorStand)entity).getChestplate();
      if (item != null && item.getType() == Material.ANVIL) {
         ItemMeta meta = item.getItemMeta();
         return meta.hasDisplayName() && meta.getDisplayName().equals(uuid);
      }
      return false;
   }

   public static String getSavedUUID(Entity entity) {
      if (!(entity instanceof ArmorStand)) {
         return null;
      }
      ItemStack item = ((ArmorStand)entity).getChestplate();
      if (item != null && item.getType() == Material.ANVIL) {
         ItemMeta meta = item.getItemMeta();
         if (meta.hasDisplayName()) {
            return meta.getDisplayName();
         }
      }
      return null;
   }

   public static void setBoundingBox(ArmorStand stand, BoundingBoxData data) {
      try {
         double height = data.getHeight();
         double xWidth = data.getXWidth();
         double zWidth = data.getZWidth();
         Location loc = stand.getLocation();
         Object nms = handle(stand);
         Object aabb = aabbConstructor.newInstance(
            loc.getX() - xWidth, loc.getY() + data.getHeightOffset(), loc.getZ() - zWidth, loc.getX() + xWidth, loc.getY() + height, loc.getZ() + zWidth
         );
         entitySetBoundingBox.invoke(nms, aabb);
         Object dimensions = dimensionsFixed.invoke(null, (float)data.getXWidth() * 2.0F, (float)data.getHeight());
         entityDimensions.set(nms, dimensions);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void drawBoundingBox(ArmorStand stand, BoundingBoxData data) {
      double height = data.getHeight();
      double xWidth = data.getXWidth();
      double zWidth = data.getZWidth();
      double offset = data.getHeightOffset();
      World world = stand.getWorld();
      Location l = stand.getLocation();
      double[] ys = {offset, height};
      for (double y : ys) {
         drawLine(new Location(world, l.getX() + xWidth, l.getY() + y, l.getZ() + zWidth), new Location(world, l.getX() - xWidth, l.getY() + y, l.getZ() + zWidth));
         drawLine(new Location(world, l.getX() - xWidth, l.getY() + y, l.getZ() - zWidth), new Location(world, l.getX() - xWidth, l.getY() + y, l.getZ() + zWidth));
         drawLine(new Location(world, l.getX() - xWidth, l.getY() + y, l.getZ() - zWidth), new Location(world, l.getX() + xWidth, l.getY() + y, l.getZ() - zWidth));
         drawLine(new Location(world, l.getX() + xWidth, l.getY() + y, l.getZ() + zWidth), new Location(world, l.getX() + xWidth, l.getY() + y, l.getZ() - zWidth));
      }
      double[][] corners = {{xWidth, zWidth}, {xWidth, -zWidth}, {-xWidth, -zWidth}, {-xWidth, zWidth}};
      for (double[] c : corners) {
         drawLine(new Location(world, l.getX() + c[0], l.getY() + offset, l.getZ() + c[1]), new Location(world, l.getX() + c[0], l.getY() + height, l.getZ() + c[1]));
      }
   }

   private static List<Location> buildLine(Location from, Location to, double step) {
      List<Location> points = new ArrayList<>();
      if (from == null) {
         return points;
      }
      points.add(from);
      if (to == null || !from.getWorld().equals(to.getWorld())) {
         return points;
      }
      World world = from.getWorld();
      double distance = from.distance(to);
      double count = distance / step;
      if (count < 1.0) {
         return points;
      }
      double dx = (to.getX() - from.getX()) / count;
      double dy = (to.getY() - from.getY()) / count;
      double dz = (to.getZ() - from.getZ()) / count;
      double travelled = 0.0;
      Location current = from;
      while (step > 0.0 && travelled <= distance) {
         current = new Location(world, current.getX() + dx, current.getY() + dy, current.getZ() + dz, current.getYaw(), current.getPitch());
         points.add(current);
         travelled += step;
      }
      return points;
   }

   private static void drawLine(Location from, Location to) {
      for (Location point : buildLine(from, to, 0.1)) {
         point.getWorld().spawnParticle(Particle.DUST, point, 1, new DustOptions(Color.fromBGR(255, 0, 0), 1.0F));
      }
   }

   public static Player getPlayerFromEntityPlayer(Object nmsPlayer) {
      try {
         return (Player)entityGetBukkitEntity.invoke(nmsPlayer);
      } catch (Exception e) {
         return null;
      }
   }

   public static void setVehicle(Object nmsEntity, Object vehicle) {
      try {
         entityVehicle.set(nmsEntity, vehicle);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static Object getVehicle(Object nmsEntity) {
      try {
         return entityVehicle.get(nmsEntity);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void setCustomPassengerList(Entity entity) {
      entity.setMetadata("VEHICLES_UNDERWATER_VEHICLE", new FixedMetadataValue(VehiclesMain.getPlugin(), true));
   }

   public static void setCamera(Player player, Object nmsEntity) {
      try {
         send(player, cameraPacketConstructor.newInstance(nmsEntity));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static Object getNMSPlayer(Player player) {
      try {
         return playerHandle(player);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static Object getNMSEntity(Entity entity) {
      try {
         return handle(entity);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static Object sendCameraEntity(Player player, Location location) {
      try {
         Object level = craftWorldGetHandle.invoke(location.getWorld());
         Object creeper = creeperConstructor.newInstance(creeperEntityType, level);
         entitySnapTo.invoke(creeper, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
         entitySetInvisible.invoke(creeper, true);
         sendSpawnPacket(player, creeper);
         return creeper;
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static Object createNMSArmorStand(Location location, boolean invisible, boolean small, boolean arms) {
      try {
         Object level = craftWorldGetHandle.invoke(location.getWorld());
         Object stand = armorStandConstructor.newInstance(armorStandEntityType, level);
         entitySnapTo.invoke(stand, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
         if (invisible) {
            entitySetInvisible.invoke(stand, true);
         }
         if (small) {
            armorStandSetSmall.invoke(stand, true);
         }
         if (arms) {
            armorStandSetShowArms.invoke(stand, true);
         }
         return stand;
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void setArmorStandPose(Object nmsStand, Object pose) {
      try {
         armorStandSetPose.invoke(nmsStand, pose);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static Object getArmorStandPose(Object nmsStand) {
      try {
         return armorStandGetPose.invoke(nmsStand);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void sendSpawnPacket(Player player, Object nmsEntity) {
      try {
         Object pos = entityBlockPosition.invoke(nmsEntity);
         send(player, addEntityPacketConstructor.newInstance(nmsEntity, 0, pos));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void sendEquipmentPacket(Player player, Object nmsEntity, Object equipmentList) {
      try {
         int id = (Integer)entityGetId.invoke(nmsEntity);
         send(player, equipmentPacketConstructor.newInstance(id, equipmentList));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void sendEmptyEquipmentPacket(Player player, Object nmsEntity) {
      try {
         List<Object> pairs = new ArrayList<>();
         Object empty = craftItemStackAsNMSCopy.invoke(null, new ItemStack(Material.AIR));
         for (Object slot : equipmentSlots) {
            pairs.add(pairConstructor.newInstance(slot, empty));
         }
         int id = (Integer)entityGetId.invoke(nmsEntity);
         send(player, equipmentPacketConstructor.newInstance(id, pairs));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static Object getArmorStandEquipment(Object nmsStand) {
      try {
         List<Object> pairs = new ArrayList<>();
         for (Object slot : equipmentSlots) {
            pairs.add(pairConstructor.newInstance(slot, livingGetItemBySlot.invoke(nmsStand, slot)));
         }
         return pairs;
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void sendMetadataPacket(Player player, Object nmsEntity, Object entityData) {
      try {
         int id = (Integer)entityGetId.invoke(nmsEntity);
         Object values = synchedGetNonDefaultValues.invoke(entityData);
         if (values == null) {
            return;
         }
         send(player, entityDataPacketConstructor.newInstance(id, values));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static Object getMetadata(Object nmsEntity) {
      try {
         return entityGetEntityData.invoke(nmsEntity);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void sendLocationPacket(Object nmsEntity, Location location, Player player) {
      try {
         entitySnapTo.invoke(nmsEntity, location.getX(), location.getY(), location.getZ(), location.getYaw(), 0.0F);
         Object change = positionMoveRotationOf.invoke(null, nmsEntity);
         boolean onGround = (Boolean)entityOnGround.invoke(nmsEntity);
         int id = (Integer)entityGetId.invoke(nmsEntity);
         send(player, teleportPacketFactory.invoke(null, id, change, Set.of(), onGround));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void sendPacket(Object packet, Player player) {
      try {
         send(player, packet);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static Object getLocationPacket(Object nmsEntity, Location location) {
      try {
         int id = (Integer)entityGetId.invoke(nmsEntity);
         Object position = vec3Constructor.newInstance(location.getX(), location.getY(), location.getZ());
         Object delta = vec3Constructor.newInstance(0.0, 0.0, 0.0);
         Object change = positionMoveRotationConstructor.newInstance(position, delta, location.getYaw(), location.getPitch());
         return teleportPacketFactory.invoke(null, id, change, Set.of(), false);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void sendYawPacket(Object nmsEntity, float yaw, Player player) {
      try {
         send(player, headRotationPacketConstructor.newInstance(nmsEntity, (byte)(yaw * 256.0F / 360.0F)));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void setEntityInvisible(Object nmsEntity, Player player) {
      try {
         entitySetInvisible.invoke(nmsEntity, true);
         sendMetadataPacket(player, nmsEntity, entityGetEntityData.invoke(nmsEntity));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void sendRemovePacket(Player player, Object nmsEntity) {
      try {
         int id = (Integer)entityGetId.invoke(nmsEntity);
         send(player, removeEntitiesPacketConstructor.newInstance((Object)new int[]{id}));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static double getCurrentFuel(ArmorStand stand, VehicleSubType subType) {
      ItemStack boots = stand.getEquipment().getBoots();
      if (boots != null && boots.getType() == Material.STICK) {
         ItemMeta meta = boots.getItemMeta();
         return !meta.hasDisplayName() ? subType.getFuelCapacity() : Double.parseDouble(meta.getDisplayName().replace(",", "."));
      }
      return subType.getFuelCapacity();
   }

   public static void setCurrentFuel(ArmorStand stand, double fuel) {
      ItemStack item = new ItemStack(Material.STICK);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName("" + fuel);
      item.setItemMeta(meta);
      stand.getEquipment().setBoots(item);
   }

   public static BlockPlaceEvent createBlockPlaceEvent(Player player, BlockState replaced, Block block, ItemStack item) {
      try {
         BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block, item, player, true, EquipmentSlot.CHEST);
         Bukkit.getServer().getPluginManager().callEvent(event);
         return event;
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void setModelGroup(ArmorStand stand, int group) {
      PersistentDataContainer container = stand.getPersistentDataContainer();
      container.set(modelGroupKey, PersistentDataType.INTEGER, group);
   }

   public static int getModelGroup(ArmorStand stand) {
      PersistentDataContainer container = stand.getPersistentDataContainer();
      return !container.has(modelGroupKey, PersistentDataType.INTEGER) ? 0 : container.get(modelGroupKey, PersistentDataType.INTEGER);
   }

   public static int getWorldMinY(World world) {
      return world.getMinHeight();
   }

   public static int getWorldMaxY(World world) {
      return world.getMaxHeight();
   }

   /** The server has no versioned CraftBukkit package any more. */
   public static String getVersion() {
      return "";
   }
}
