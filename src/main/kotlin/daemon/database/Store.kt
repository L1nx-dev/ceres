package ceres.main

import kotlinx.serialization.json.*
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.time.Instant
import kotlin.io.path.createDirectories
import ceres.system.*
import ceres.core.*

data class Hit(val sessionId: Long, val messageId: Long, val snippet: String)

/** All SQL lives here. One connection, every method synchronized. */
class Store(path: Path) : AutoCloseable {
  private val db: Connection

  init {
    path.parent.createDirectories()
    db = DriverManager.getConnection("jdbc:sqlite:$path")
    db.createStatement().use { st ->
      st.execute("PRAGMA journal_mode=WAL")
      st.execute("PRAGMA synchronous=NORMAL")
      st.execute("PRAGMA foreign_keys=ON")
      st.execute("PRAGMA busy_timeout=5000")
    }
    migrate()
  }

  private fun migrate() {
    val version = db.createStatement().use { st ->
      st.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
    }
    if (version < 1) inTransaction {
      exec("""CREATE TABLE sessions(
        id INTEGER PRIMARY KEY,
        name TEXT,
        started_at TEXT NOT NULL)""")
      exec("""CREATE TABLE messages(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id INTEGER NOT NULL REFERENCES sessions(id),
        kind TEXT NOT NULL,              -- text | tool_calls | tool_result
        role TEXT,
        content TEXT,
        call_id TEXT,
        calls_json TEXT,
        ok INTEGER NOT NULL DEFAULT 1,
        block_type TEXT NOT NULL DEFAULT 'CHAT',
        source TEXT NOT NULL DEFAULT 'USER',
        ref TEXT,
        state TEXT NOT NULL DEFAULT 'active',   -- active | compacted
        ord INTEGER,                            -- render order override; NULL = id
        replaced_by INTEGER REFERENCES messages(id),
        created_at TEXT NOT NULL)""")
      exec("CREATE UNIQUE INDEX sessions_name ON sessions(name)")
      exec("CREATE INDEX messages_session ON messages(session_id, state)")
      exec("CREATE INDEX messages_ref ON messages(ref)")
      exec("""CREATE VIRTUAL TABLE messages_fts
        USING fts5(content, content='messages', content_rowid='id')""")
      exec("""CREATE TRIGGER messages_ai AFTER INSERT ON messages BEGIN
        INSERT INTO messages_fts(rowid, content) VALUES (new.id, new.content);
      END""")
      exec("""CREATE TRIGGER messages_ad AFTER DELETE ON messages BEGIN
        INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.id, old.content);
      END""")
      exec("PRAGMA user_version = 1")
    }
  }

  private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

  private fun inTransaction(block: () -> Unit) {
    db.autoCommit = false
    try {
      block()
      db.commit()
    } catch (e: Exception) {
      db.rollback()
      throw e
    } finally {
      db.autoCommit = true
    }
  }

  // ==== SESSIONS ====

  data class SessionInfo(val id: Long, val name: String?, val startedAt: String, val messages: Int, val preview: String?)
    
  @Synchronized
  fun exists(sid: Long): Boolean =
    db.prepareStatement("SELECT 1 FROM sessions WHERE id = ?").use { ps ->
      ps.setLong(1, sid); ps.executeQuery().use { it.next() }
    }

  @Synchronized
  fun lastSession(): Long? =
    db.createStatement().use { st ->
      st.executeQuery("SELECT id FROM sessions ORDER BY id DESC LIMIT 1").use {
        if (it.next()) it.getLong(1) else null
      }
    }

  @Synchronized
  fun newSession(name: String? = null): Long =
    db.prepareStatement("INSERT INTO sessions(started_at, name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS).use { ps ->
      ps.setString(1, Instant.now().toString())
      ps.setString(2, name)
      ps.executeUpdate()
      ps.generatedKeys.use { it.next(); it.getLong(1) }
    }

  @Synchronized
  fun byName(name: String): Long? =
    db.prepareStatement("SELECT id FROM sessions WHERE name = ?").use { ps ->
      ps.setString(1, name)
      ps.executeQuery().use { if (it.next()) it.getLong(1) else null }
    }

  @Synchronized
  fun resolve(ref: String): Long? = byName(ref) ?: ref.toLongOrNull()?.takeIf { exists(it) }

  @Synchronized
  fun rename(sid: Long, name: String) {
    db.prepareStatement("UPDATE sessions SET name = ? WHERE id = ?").use { ps ->
      ps.setString(1, name); ps.setLong(2, sid); ps.executeUpdate()
    }
  }

  @Synchronized
  fun delete(sid: Long) = inTransaction {
    db.prepareStatement("DELETE FROM messages WHERE session_id = ?").use { it.setLong(1, sid); it.executeUpdate() }
    db.prepareStatement("DELETE FROM sessions WHERE id = ?").use { it.setLong(1, sid); it.executeUpdate() }
  }

  @Synchronized
  fun sessions(current: Long, limit: Int = 20): List<SessionInfo> =
    db.prepareStatement("""
      SELECT s.id, s.name, s.started_at,
        (SELECT COUNT(*) FROM messages m WHERE m.session_id = s.id),
        (SELECT substr(content, 1, 60) FROM messages m
           WHERE m.session_id = s.id AND kind = 'text' AND role = 'USER' AND block_type = 'CHAT'
           ORDER BY id LIMIT 1)
      FROM sessions s
      WHERE s.id = ? OR s.name IS NOT NULL
         OR EXISTS (SELECT 1 FROM messages m WHERE m.session_id = s.id AND kind = 'text' AND role = 'USER')
      ORDER BY s.id DESC LIMIT ?""").use { ps ->
      ps.setLong(1, current); ps.setInt(2, limit)
      ps.executeQuery().use { rs ->
        buildList { while (rs.next()) add(SessionInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5))) }
      }
    }

  // ==== MESSAGES ====

  @Synchronized
  fun append(sid: Long, msg: Msg) {
    var kind: String
    var role: String? = null
    var content: String? = null
    var callId: String? = null
    var calls: String? = null
    var ok = true
    when (msg) {
      is Msg.Text -> { kind = "text"; role = msg.role.name; content = msg.content }
      is Msg.ToolCalls -> { kind = "tool_calls"; content = msg.content; calls = callsToJson(msg.calls) }
      is Msg.ToolResult -> { kind = "tool_result"; callId = msg.callId; content = msg.content; ok = msg.ok }
    }
    db.prepareStatement(
      """INSERT INTO messages(session_id, kind, role, content, call_id, calls_json, ok,
           block_type, source, ref, created_at)
         VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
      Statement.RETURN_GENERATED_KEYS,
    ).use { ps ->
      ps.setLong(1, sid)
      ps.setString(2, kind)
      ps.setString(3, role)
      ps.setString(4, content)
      ps.setString(5, callId)
      ps.setString(6, calls)
      ps.setInt(7, if (ok) 1 else 0)
      ps.setString(8, msg.type.name)
      ps.setString(9, msg.source.name)
      ps.setString(10, msg.ref)
      ps.setString(11, Instant.now().toString())
      ps.executeUpdate()
      msg.id = ps.generatedKeys.use { it.next(); it.getLong(1) }
    }
  }

  /** Active messages in render order. Repairs a log cut off mid tool-call. */
  @Synchronized
  fun load(sid: Long): MutableList<Msg> {
    val out = mutableListOf<Msg>()
    db.prepareStatement(
      """SELECT id, kind, role, content, call_id, calls_json, ok, block_type, source, ref
         FROM messages WHERE session_id = ? AND state = 'active'
         ORDER BY COALESCE(ord, id)""",
    ).use { ps ->
      ps.setLong(1, sid)
      ps.executeQuery().use { rs ->
        while (rs.next()) {
          val type = BlockType.valueOf(rs.getString("block_type"))
          val source = Source.valueOf(rs.getString("source"))
          val ref = rs.getString("ref")
          val msg: Msg = when (val kind = rs.getString("kind")) {
            "text" -> Msg.Text(Role.valueOf(rs.getString("role")), rs.getString("content"), type, source, ref)
            "tool_calls" -> Msg.ToolCalls(callsFromJson(rs.getString("calls_json")), rs.getString("content"), type, source, ref)
            "tool_result" -> Msg.ToolResult(rs.getString("call_id"), rs.getString("content"), rs.getInt("ok") == 1, type, source, ref)
            else -> error("unknown message kind: $kind")
          }
          msg.id = rs.getLong("id")
          out += msg
        }
      }
    }
    repair(sid, out)
    return out
  }

  private fun repair(sid: Long, msgs: MutableList<Msg>) {
    var i = 0
    while (i < msgs.size) {
      val m = msgs[i]
      if (m is Msg.ToolCalls) {
        var j = i + 1
        val done = mutableSetOf<String>()
        while (j < msgs.size && msgs[j] is Msg.ToolResult) {
          done += (msgs[j] as Msg.ToolResult).callId
          j++
        }
        for (c in m.calls.filter { it.id !in done }) {
          val r = Msg.ToolResult(c.id, "error: interrupted", ok = false)
          append(sid, r)
          msgs.add(j++, r)
        }
        i = j
      } else i++
    }
  }

  // ==== SEARCH ====

  @Synchronized
  fun search(query: String, limit: Int = 10): List<Hit> =
    db.prepareStatement(
      """SELECT m.session_id, m.id, snippet(messages_fts, 0, '[', ']', '…', 12)
         FROM messages_fts JOIN messages m ON m.id = messages_fts.rowid
         WHERE messages_fts MATCH ? ORDER BY rank LIMIT ?""",
    ).use { ps ->
      ps.setString(1, query)
      ps.setInt(2, limit)
      ps.executeQuery().use { rs ->
        buildList { while (rs.next()) add(Hit(rs.getLong(1), rs.getLong(2), rs.getString(3))) }
      }
    }

  @Synchronized
  fun backup(to: Path) {
    exec("VACUUM INTO '${to.toString().replace("'", "''")}'")
  }

  override fun close() = db.close()

  // ==== JSON ====

  private fun callsToJson(calls: List<ToolCall>): String = buildJsonArray {
    for (c in calls) addJsonObject {
      put("id", c.id)
      put("name", c.name)
      put("arguments", c.arguments)
    }
  }.toString()

  private fun callsFromJson(s: String): List<ToolCall> =
    Json.parseToJsonElement(s).jsonArray.map {
      val o = it.jsonObject
      ToolCall(
        o.getValue("id").jsonPrimitive.content,
        o.getValue("name").jsonPrimitive.content,
        o.getValue("arguments").jsonPrimitive.content,
      )
    }
}
