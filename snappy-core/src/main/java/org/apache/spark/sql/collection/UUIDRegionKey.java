package org.apache.spark.sql.collection;

import java.util.UUID;

/**
 * Created by soubhikc on 27/7/15.
 */
public final class UUIDRegionKey implements java.io.Serializable, Comparable<UUIDRegionKey> {
  private final UUID uuid;
  private final int bucketId;

  public UUIDRegionKey(int bucketId) {
    this.uuid = UUID.randomUUID();
    this.bucketId = bucketId;
  }

  public UUIDRegionKey(int bucketId, UUID batchID) {
    this.uuid = batchID;
    this.bucketId = bucketId;
  }

  public static Object parseAndGetBucketId(String uuidStr) {
    final int colonIdx = uuidStr.lastIndexOf(":");
    if (colonIdx < 0 || uuidStr.split("-").length != 5) {
      return uuidStr;
    }

    return Integer.parseInt(uuidStr.substring(colonIdx + 1));
  }

  @Override
  public int compareTo(UUIDRegionKey other) {
    final int uuidCompare = uuid.compareTo(other.uuid);
    return (uuidCompare != 0 ? uuidCompare :
        this.bucketId < other.bucketId ? -1 :
            this.bucketId > other.bucketId ? 1 :
                0);
  }

  public String toString() {
    return uuid.toString() + ":" + this.bucketId;
  }

  public UUID getUUID() {
    return uuid;
  }

  public int getBucketId() {
    return bucketId;
  }
}
