package dev.solomaster.research;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The {@code run_item} table: the inbox queue and retry list (ADR-0004 item 9). Every query is
 * parameterized. A queued or failed item is picked up again by the next Run now.
 */
class RunItems {

  static final String QUEUED = "queued";
  static final String DONE = "done";
  static final String FAILED = "failed";

  /** One intake item and what happened to it. */
  record RunItem(
      long id,
      String channel,
      String itemKey,
      String title,
      String status,
      int attempts,
      String lastError,
      String briefName,
      Instant updatedAt) {}

  private static final String COLUMNS =
      "id, channel, item_key, title, status, attempts, last_error, brief_name, updated_at";

  private final JdbcClient db;

  RunItems(JdbcClient db) {
    this.db = db;
  }

  /** Adds a new item to the queue; an item already known for its channel is left as it is. */
  void addQueued(String channel, String itemKey, String title, Instant now) {
    insert(channel, itemKey, title, QUEUED, null, now);
  }

  /** Records an item whose brief already exists in the vault, so it is never briefed again. */
  void addDone(String channel, String itemKey, String title, String briefName, Instant now) {
    insert(channel, itemKey, title, DONE, briefName, now);
  }

  /** Queued and failed items, oldest first. */
  List<RunItem> pending(int limit) {
    return db.sql(
            "SELECT "
                + COLUMNS
                + " FROM run_item WHERE status IN (?, ?) ORDER BY created_at, id LIMIT ?")
        .params(QUEUED, FAILED, limit)
        .query(RunItems::map)
        .list();
  }

  /** Most recently changed first. */
  List<RunItem> recent(int limit) {
    return db.sql("SELECT " + COLUMNS + " FROM run_item ORDER BY updated_at DESC, id DESC LIMIT ?")
        .params(limit)
        .query(RunItems::map)
        .list();
  }

  void markDone(long id, String briefName, Instant now) {
    db.sql(
            "UPDATE run_item SET status = ?, attempts = attempts + 1, last_error = NULL,"
                + " brief_name = ?, updated_at = ? WHERE id = ?")
        .params(DONE, briefName, now.toString(), id)
        .update();
  }

  void markFailed(long id, String reason, Instant now) {
    db.sql(
            "UPDATE run_item SET status = ?, attempts = attempts + 1, last_error = ?,"
                + " updated_at = ? WHERE id = ?")
        .params(FAILED, reason, now.toString(), id)
        .update();
  }

  private void insert(
      String channel, String itemKey, String title, String status, String briefName, Instant now) {
    db.sql(
            "INSERT INTO run_item (channel, item_key, title, status, brief_name, created_at,"
                + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (channel, item_key) DO"
                + " NOTHING")
        .params(channel, itemKey, title, status, briefName, now.toString(), now.toString())
        .update();
  }

  private static RunItem map(java.sql.ResultSet row, int rowNumber) throws java.sql.SQLException {
    return new RunItem(
        row.getLong("id"),
        row.getString("channel"),
        row.getString("item_key"),
        row.getString("title"),
        row.getString("status"),
        row.getInt("attempts"),
        row.getString("last_error"),
        row.getString("brief_name"),
        Instant.parse(row.getString("updated_at")));
  }
}
