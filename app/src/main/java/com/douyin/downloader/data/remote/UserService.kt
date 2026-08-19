package com.douyin.downloader.data.remote

import com.douyin.downloader.IUserService
import android.os.RemoteException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Shizuku UserService 实现。
 * 以 shell 身份运行，通过 run-as 命令读取目标 App 的 SharedPrefs Cookie。
 *
 * 注意：此进程的 Context 受限（无 ContentResolver 等），仅适合执行系统命令。
 */
class UserService : IUserService.Stub() {

    @Throws(RemoteException::class)
    override fun destroy() {
        System.exit(0)
    }

    @Throws(RemoteException::class)
    override fun listSharedPrefs(pkg: String): String {
        return exec("run-as $pkg ls shared_prefs")
    }

    @Throws(RemoteException::class)
    override fun readSharedPrefsFile(pkg: String, file: String): String {
        return exec("run-as $pkg cat shared_prefs/$file")
    }

    private fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: IOException) {
            throw RemoteException(e.message)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RemoteException(e.message)
        }
    }
}
