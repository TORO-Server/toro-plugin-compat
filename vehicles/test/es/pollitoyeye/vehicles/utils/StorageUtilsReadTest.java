package es.pollitoyeye.vehicles.utils;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Checks the stored list format and its limits with the JDK only (no server, no test framework).
 * Not part of the jar. See "Testing StorageUtils" in the README for how to run it.
 */
public final class StorageUtilsReadTest {
   private static final int SLOTS = 27;
   private static final int MAX_ITEM_BYTES = 2097152;
   private static int failures;

   public static void main(String[] args) throws Exception {
      accepts("empty list, as written by the plugin", writtenByPlugin(new ArrayList<>()), 0, 0);
      accepts("one item, as written by the plugin", writtenByPlugin(items(1, 16)), 1, 16);
      accepts("full storage, as written by the plugin", writtenByPlugin(items(SLOTS, 1024)), SLOTS, 1024);
      accepts("full storage from writeStoredList", StorageUtils.writeStoredList(items(SLOTS, 1024)), SLOTS, 1024);
      accepts("largest item", writtenByPlugin(items(1, MAX_ITEM_BYTES)), 1, MAX_ITEM_BYTES);

      rejects("more items than the storage has slots", writtenByPlugin(items(SLOTS + 1, 16)));
      rejects("item over the size limit", writtenByPlugin(items(1, MAX_ITEM_BYTES + 1)));
      rejects("stream over the size limit", writtenByPlugin(items(9, MAX_ITEM_BYTES)));
      rejects("another class in the list", serialize(listOf(new Date())));
      rejects("another class instead of the list", serialize(new HashMap<String, String>()));
      rejects("a string in the list", serialize(listOf("text")));
      rejects("a null in the list", serialize(listOf((Object)null)));
      rejects("a list in the list", serialize(listOf(listOf(listOf(new byte[1])))));
      rejects("a byte array instead of the list", serialize(new byte[16]));
      rejects("data that is not serialised at all", new byte[] {1, 2, 3, 4});

      if (failures > 0) {
         System.out.println(failures + " check(s) failed");
         System.exit(1);
      }
      System.out.println("All checks passed");
   }

   private static void accepts(String name, byte[] stored, int count, int itemLength) {
      try {
         List<byte[]> read = StorageUtils.readStoredList(stored);
         byte[] expected = new byte[itemLength];
         Arrays.fill(expected, (byte)7);
         boolean same = read.size() == count && read.stream().allMatch(data -> Arrays.equals(data, expected));
         report(name, same, same ? "read " + read.size() : "wrong content, read " + read.size());
      } catch (Exception e) {
         report(name, false, e.toString());
      }
   }

   private static void rejects(String name, byte[] stored) {
      try {
         report(name, false, "was accepted with " + StorageUtils.readStoredList(stored).size() + " entries");
      } catch (Exception e) {
         report(name, true, e.toString());
      }
   }

   private static void report(String name, boolean ok, String detail) {
      System.out.println((ok ? "ok    " : "FAIL  ") + name + " (" + detail + ")");
      if (!ok) {
         failures++;
      }
   }

   private static ArrayList<byte[]> items(int count, int length) {
      byte[] data = new byte[length];
      Arrays.fill(data, (byte)7);
      ArrayList<byte[]> list = new ArrayList<>();
      for (int i = 0; i < count; i++) {
         list.add(data.clone());
      }
      return list;
   }

   private static ArrayList<Object> listOf(Object element) {
      ArrayList<Object> list = new ArrayList<>();
      list.add(element);
      return list;
   }

   /** The same steps as saveInventoryToItem has always used, so this is also what older versions wrote. */
   private static byte[] writtenByPlugin(ArrayList<byte[]> list) throws Exception {
      return serialize(list);
   }

   private static byte[] serialize(Object value) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bytes);
      out.writeObject(value);
      byte[] written = bytes.toByteArray();
      out.close();
      return written;
   }
}
