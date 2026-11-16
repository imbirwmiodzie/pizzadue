package com.vibepizzaiolo.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/** Plain SQL over a connection pool. The statements run on PostgreSQL and on H2 (tests, and no-database runs). */
class Db(private val source: DataSource) {
    fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = source.connection.use { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }
    }

    fun <T> one(sql: String, vararg args: Any?, map: (ResultSet) -> T): T? = query(sql, *args, map = map).firstOrNull()

    fun update(sql: String, vararg args: Any?): Int = source.connection.use { it.exec(sql, *args) }

    /** Several statements that succeed or fail together. */
    fun <T> tx(block: (Connection) -> T): T = source.connection.use { c ->
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Throwable) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    fun migrate() = source.connection.use { c -> c.createStatement().use { st -> SCHEMA.forEach(st::execute) } }

    companion object {
        fun connect(url: String, user: String?, password: String?): Db {
            val cfg = HikariConfig().apply {
                jdbcUrl = url
                if (user != null) username = user
                if (password != null) this.password = password
                maximumPoolSize = 8
            }
            return Db(HikariDataSource(cfg))
        }

        private val SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(36) PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, name VARCHAR(30) NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                spice INT, base VARCHAR(16), style VARCHAR(16), temp_c INT, oven VARCHAR(16),
                created_at BIGINT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS sessions (
                token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                expires_at BIGINT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS photos (
                id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at BIGINT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS pizzas (
                id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                photo_id VARCHAR(36) NOT NULL, type VARCHAR(24) NOT NULL, style VARCHAR(16) NOT NULL, base VARCHAR(16) NOT NULL,
                spice INT NOT NULL, temp_c INT NOT NULL, oven VARCHAR(16) NOT NULL, bake_sec INT, hydration INT,
                caption VARCHAR(200) NOT NULL, created_at BIGINT NOT NULL, hidden BOOLEAN NOT NULL DEFAULT FALSE)""",
            "CREATE INDEX IF NOT EXISTS pizzas_created ON pizzas(created_at)",
            """CREATE TABLE IF NOT EXISTS swipes (
                pizza_id VARCHAR(36) NOT NULL REFERENCES pizzas(id) ON DELETE CASCADE,
                user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                liked BOOLEAN NOT NULL, PRIMARY KEY (pizza_id, user_id))""",
            """CREATE TABLE IF NOT EXISTS comments (
                id VARCHAR(36) PRIMARY KEY, pizza_id VARCHAR(36) NOT NULL REFERENCES pizzas(id) ON DELETE CASCADE,
                user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                body VARCHAR(500) NOT NULL, created_at BIGINT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS invites (
                id VARCHAR(36) PRIMARY KEY, pizza_id VARCHAR(36) NOT NULL REFERENCES pizzas(id) ON DELETE CASCADE,
                from_user VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                to_user VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, UNIQUE (pizza_id, from_user))""",
            """CREATE TABLE IF NOT EXISTS messages (
                id VARCHAR(36) PRIMARY KEY, invite_id VARCHAR(36) NOT NULL REFERENCES invites(id) ON DELETE CASCADE,
                user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                body VARCHAR(500) NOT NULL, created_at BIGINT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS reports (
                pizza_id VARCHAR(36) NOT NULL REFERENCES pizzas(id) ON DELETE CASCADE,
                user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                reason VARCHAR(500) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY (pizza_id, user_id))""",
            """CREATE TABLE IF NOT EXISTS blocks (
                user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                blocked_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                PRIMARY KEY (user_id, blocked_id))""",
        )
    }
}

fun Connection.exec(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { st ->
    args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
    st.executeUpdate()
}

fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeIf { !wasNull() }
fun ResultSet.boolOrNull(column: String): Boolean? = getBoolean(column).takeIf { !wasNull() }
