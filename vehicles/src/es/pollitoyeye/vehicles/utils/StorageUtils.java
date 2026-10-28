package es.pollitoyeye.vehicles.utils;

import es.pollitoyeye.vehicles.VehiclesMain;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Item (de)serialisation for the vehicle storage.
 *
 * <p>The original implementation called obfuscated NMS methods. Items are now serialised with
 * Paper's {@link ItemStack#serializeAsBytes()} (compressed NBT including the data version). Byte
 * arrays written by the old implementation (compressed NBT of an item from Minecraft 1.14-1.20.4,
 * without a data version) are still readable: the missing data version is filled in before the data
 * is handed to Paper's data fixers.
 */
public class StorageUtils {
   /** DataVersion of Minecraft 1.20.4, the last version supported by the original plugin. */
   private static final int LEGACY_DATA_VERSION = 3700;

   public static void saveInventoryToItem(ItemStack[] contents, ItemStack target) {
      NamespacedKey key = new NamespacedKey(VehiclesMain.getPlugin(), "Storage");
      ItemMeta meta = target.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      ArrayList<byte[]> serialized = new ArrayList<>();
      for (ItemStack item : contents) {
         if (item != null) {
            serialized.add(serializeItemStack(item));
         }
      }

      try {
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ObjectOutputStream out = new ObjectOutputStream(bytes);
         out.writeObject(serialized);
         container.set(key, PersistentDataType.BYTE_ARRAY, bytes.toByteArray());
         out.close();
      } catch (Exception e) {
         e.printStackTrace();
      }

      target.setItemMeta(meta);
   }

   @SuppressWarnings("unchecked")
   public static void loadInventoryIntoArmorStand(ArmorStand stand, ItemStack source) {
      ArrayList<ItemStack> items = new ArrayList<>();
      NamespacedKey key = new NamespacedKey(VehiclesMain.getPlugin(), "Storage");
      ItemMeta meta = source.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
         try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(container.get(key, PersistentDataType.BYTE_ARRAY));
            ObjectInputStream in = new ObjectInputStream(bytes);
            for (byte[] data : (ArrayList<byte[]>)in.readObject()) {
               items.add(deserializeItemStack(data));
            }
            in.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      ItemStack shulker = new ItemStack(Material.SHULKER_BOX, 1);
      BlockStateMeta shulkerMeta = (BlockStateMeta)shulker.getItemMeta();
      ShulkerBox box = (ShulkerBox)shulkerMeta.getBlockState();
      box.getInventory().setContents(items.toArray(new ItemStack[0]));
      box.update();
      shulkerMeta.setBlockState(box);
      shulker.setItemMeta(shulkerMeta);
      stand.getEquipment().setLeggings(shulker);
   }

   public static byte[] serializeItemStack(ItemStack item) {
      if (item == null) {
         return null;
      }
      return item.serializeAsBytes();
   }

   public static ItemStack deserializeItemStack(byte[] data) {
      if (data == null) {
         return null;
      }
      try {
         return ItemStack.deserializeBytes(withDataVersion(data));
      } catch (Throwable t) {
         t.printStackTrace();
         return null;
      }
   }

   /** Adds the legacy data version to data written by the pre-26.x implementation, which had none. */
   private static byte[] withDataVersion(byte[] data) throws Exception {
      Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
      Class<?> accounter = Class.forName("net.minecraft.nbt.NbtAccounter");
      Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
      Object limit = accounter.getMethod("unlimitedHeap").invoke(null);
      Object tag = nbtIo.getMethod("readCompressed", java.io.InputStream.class, accounter).invoke(null, new ByteArrayInputStream(data), limit);
      if ((Boolean)compound.getMethod("contains", String.class).invoke(tag, "DataVersion")) {
         return data;
      }
      Method putInt = compound.getMethod("putInt", String.class, int.class);
      putInt.invoke(tag, "DataVersion", LEGACY_DATA_VERSION);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      nbtIo.getMethod("writeCompressed", compound, java.io.OutputStream.class).invoke(null, tag, out);
      return out.toByteArray();
   }
   /** Kept for source compatibility; the NMS packages are no longer versioned. */
   public static Class<?> getNMSClass(String name, String packageName) {
      try {
         return Class.forName(packageName + "." + name);
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
         return null;
      }
   }

   public static Class<?> getOBClass(String name) {
      try {
         return Class.forName("org.bukkit.craftbukkit." + name);
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
         return null;
      }
   }
}
