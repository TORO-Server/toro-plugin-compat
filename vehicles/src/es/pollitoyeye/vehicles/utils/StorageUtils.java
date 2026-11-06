package es.pollitoyeye.vehicles.utils;

import es.pollitoyeye.vehicles.VehiclesMain;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
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
 *
 * <p>The storage is kept in the item as a Java-serialised {@code ArrayList<byte[]>}. It is read
 * through a filter that accepts only that shape, within the limits below. All of this stays in this
 * one class, so that the patched jar keeps the same entries as the original jar.
 */
public class StorageUtils {
   /** DataVersion of Minecraft 1.20.4, the last version supported by the original plugin. */
   private static final int LEGACY_DATA_VERSION = 3700;
   /** Slots of the storage, which is a shulker box inventory. */
   private static final int MAX_STORED_ITEMS = 27;
   /** Largest serialised item, the same limit as the NBT read in {@link #withDataVersion(byte[])}. */
   private static final int MAX_ITEM_BYTES = 2097152;
   /** The list is depth 1, the items in it are depth 2. */
   private static final int MAX_DEPTH = 2;
   /** A full storage needs about 30: the list, its array, the byte[] class and one reference per item. */
   private static final long MAX_REFERENCES = 64L;
   private static final long MAX_STREAM_BYTES = 16L * 1024L * 1024L;

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
         container.set(key, PersistentDataType.BYTE_ARRAY, writeStoredList(serialized));
      } catch (Exception e) {
         e.printStackTrace();
      }

      target.setItemMeta(meta);
   }

   public static void loadInventoryIntoArmorStand(ArmorStand stand, ItemStack source) {
      ArrayList<ItemStack> items = new ArrayList<>();
      NamespacedKey key = new NamespacedKey(VehiclesMain.getPlugin(), "Storage");
      ItemMeta meta = source.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
         items.addAll(readStorage(container.get(key, PersistentDataType.BYTE_ARRAY)));
         retryKeptData(items);
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

   /**
    * Decodes stored data. What cannot be decoded comes back as an item holding the original bytes (see
    * {@link #keepUnreadable(byte[])}): the source item is already gone at this point and saving the
    * vehicle writes the storage contents, so an empty storage here would lose the data for good.
    */
   private static List<ItemStack> readStorage(byte[] stored) {
      List<byte[]> entries;
      try {
         entries = readStoredList(stored);
      } catch (IOException | ClassNotFoundException | RuntimeException e) {
         VehiclesMain.getPlugin().getLogger().log(Level.WARNING, "Could not read a vehicle storage, its data stays in the storage as an item", e);
         return List.of(keepUnreadable(stored));
      }

      ArrayList<ItemStack> items = new ArrayList<>();
      for (byte[] data : entries) {
         ItemStack item = deserializeItemStack(data);
         if (item != null) {
            items.add(item);
         } else {
            VehiclesMain.getPlugin().getLogger().warning("Could not read an item of a vehicle storage, its data stays in the storage as an item");
            ArrayList<byte[]> single = new ArrayList<>();
            single.add(data);
            items.add(keepUnreadable(writeStoredList(single)));
         }
      }
      return items;
   }

   /** Reads the stored list. Package-private for {@code vehicles/test}. */
   static List<byte[]> readStoredList(byte[] stored) throws IOException, ClassNotFoundException {
      try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(stored))) {
         in.setObjectInputFilter(StorageUtils::checkStoredClass);
         if (!(in.readObject() instanceof ArrayList<?> list)) {
            throw new InvalidObjectException("The stored value is not a list");
         }
         ArrayList<byte[]> entries = new ArrayList<>(list.size());
         for (Object entry : list) {
            if (!(entry instanceof byte[] data)) {
               throw new InvalidObjectException("The stored list has an entry that is not a byte array");
            }
            entries.add(data);
         }
         return entries;
      }
   }

   private static ObjectInputFilter.Status checkStoredClass(ObjectInputFilter.FilterInfo info) {
      if (info.depth() > MAX_DEPTH || info.references() > MAX_REFERENCES || info.streamBytes() > MAX_STREAM_BYTES) {
         return ObjectInputFilter.Status.REJECTED;
      }
      Class<?> serialClass = info.serialClass();
      if (serialClass == null) {
         return ObjectInputFilter.Status.UNDECIDED;
      }
      if (serialClass == ArrayList.class) {
         return ObjectInputFilter.Status.ALLOWED;
      }
      // ArrayList#readObject has its backing Object[] checked before it reads the elements.
      if (serialClass == Object[].class) {
         return info.arrayLength() <= MAX_STORED_ITEMS ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
      }
      if (serialClass == byte[].class) {
         return info.arrayLength() <= MAX_ITEM_BYTES ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
      }
      return ObjectInputFilter.Status.REJECTED;
   }

   /** Package-private for {@code vehicles/test}. */
   static byte[] writeStoredList(ArrayList<byte[]> list) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(list);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
      return bytes.toByteArray();
   }

   /**
    * An item that carries stored data which could not be read, in the stored list format. As an item it
    * survives everything the (closed source) callers do with the storage contents, and the next placement
    * of the vehicle reads it again.
    */
   private static ItemStack keepUnreadable(byte[] data) {
      ItemStack kept = new ItemStack(Material.PAPER, 1);
      ItemMeta meta = kept.getItemMeta();
      meta.setDisplayName("§c読み込めなかった保管データ");
      meta.setLore(List.of("§7捨てずに管理者に知らせてください"));
      meta.getPersistentDataContainer().set(keptDataKey(), PersistentDataType.BYTE_ARRAY, data);
      kept.setItemMeta(meta);
      return kept;
   }

   /** Reads data kept by an earlier failure again, so that it comes back once the cause is gone. */
   private static void retryKeptData(ArrayList<ItemStack> items) {
      for (int i = items.size() - 1; i >= 0; i--) {
         ItemStack item = items.get(i);
         if (!item.hasItemMeta()) {
            continue;
         }
         byte[] kept = item.getItemMeta().getPersistentDataContainer().get(keptDataKey(), PersistentDataType.BYTE_ARRAY);
         if (kept == null) {
            continue;
         }
         List<ItemStack> restored = readStorage(kept);
         if (items.size() - 1 + restored.size() <= MAX_STORED_ITEMS) {
            items.remove(i);
            items.addAll(i, restored);
         }
      }
   }

   private static NamespacedKey keptDataKey() {
      return new NamespacedKey(VehiclesMain.getPlugin(), "UnreadableStorage");
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
      Object limit;
      try {
         limit = accounter.getMethod("create", long.class).invoke(null, (long)MAX_ITEM_BYTES);
      } catch (NoSuchMethodException e) {
         limit = accounter.getMethod("unlimitedHeap").invoke(null);
      }
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
