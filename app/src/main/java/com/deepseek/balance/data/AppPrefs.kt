package com.deepseek.balance.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 全 App 唯一的 SharedPreferences 入口。
 *
 * 文件 `deepseek_balance_enc`，由 EncryptedSharedPreferences 加密（密钥在 Android Keystore）。
 * 为什么要加密：这里存着**网页令牌**（platform.deepseek.com 登录态，高权限：可查看/新建全部 API Key）
 * 和 API Key。明文 prefs 在 root / 备份导出场景下可直接读出。
 *
 * 迁移：v1.4.10 之前用明文文件 `deepseek_balance`。不能直接在旧文件上套加密壳（读取时解密失败会抛
 * SecurityException），所以换新文件名，首次访问时把旧文件内容搬进来并删除旧文件。
 *
 * 兜底：个别 ROM 上 Keystore 可能异常导致创建失败，此时退回明文文件 `deepseek_balance_plain` 并打 W 级日志，
 * 保证 App 可用。这是保底不是目标——日志出现时应当排查，而不是当作正常。
 */
object AppPrefs {

    private const val TAG = "AppPrefs"
    private const val SECURE_FILE = "deepseek_balance_enc"
    private const val LEGACY_FILE = "deepseek_balance"
    private const val PLAIN_FALLBACK_FILE = "deepseek_balance_plain"

    @Volatile
    private var cached: SharedPreferences? = null

    /** 线程安全；首次调用会访问 Keystore（数十毫秒），之后直接返回缓存实例。 */
    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val prefs = try {
                val masterKey = MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    app,
                    SECURE_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "加密存储创建失败，退回明文 prefs: ${t.javaClass.simpleName}: ${t.message}")
                app.getSharedPreferences(PLAIN_FALLBACK_FILE, Context.MODE_PRIVATE)
            }
            migrateLegacy(app, prefs)
            cached = prefs
            return prefs
        }
    }

    /** 把旧明文文件的内容搬入 [secure]，然后删除旧文件。只在旧文件非空时执行一次。 */
    private fun migrateLegacy(app: Context, secure: SharedPreferences) {
        try {
            val legacy = app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            val old = legacy.all
            if (old.isEmpty()) return
            if (secure.all.isEmpty()) {
                val editor = secure.edit()
                for ((key, value) in old) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                // 同步落盘：确认写入成功后才删旧文件
                editor.commit()
                Log.i(TAG, "明文 prefs 已迁移到加密存储，共 ${old.size} 项")
            }
            legacy.edit().clear().commit()
            app.deleteSharedPreferences(LEGACY_FILE)
        } catch (t: Throwable) {
            Log.w(TAG, "旧 prefs 迁移失败: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
