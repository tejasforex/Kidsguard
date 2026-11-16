package com.example.data.device

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceAppInfo(
    val packageName: String,
    val appName: String,
    val category: String,
    val usageMinutesToday: Long,
    val icon: Drawable?,
)

data class DeviceTelemetrySnapshot(
    val hasUsageAccess: Boolean,
    val totalMinutesToday: Long,
    val installedApps: List<DeviceAppInfo>,
)

class DeviceUsageRepository(private val context: Context) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun loadDeviceSnapshot(): DeviceTelemetrySnapshot = withContext(Dispatchers.IO) {
        val hasAccess = hasUsageStatsPermission()
        val usageByPackage = if (hasAccess) queryTodayUsageMinutes() else emptyMap()
        val apps = queryLaunchableApps(usageByPackage)
        val totalMinutes = usageByPackage.values.sum()
        DeviceTelemetrySnapshot(
            hasUsageAccess = hasAccess,
            totalMinutesToday = totalMinutes,
            installedApps = apps,
        )
    }

    private fun queryTodayUsageMinutes(): Map<String, Long> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return emptyMap()

        val result = mutableMapOf<String, Long>()
        for (stat in stats) {
            val minutes = stat.totalTimeInForeground / 60_000L
            if (minutes > 0L) {
                result[stat.packageName] = (result[stat.packageName] ?: 0L) + minutes
            }
        }
        return result
    }

    private fun queryLaunchableApps(usageByPackage: Map<String, Long>): List<DeviceAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val seenPackages = HashSet<String>()
        val list = mutableListOf<DeviceAppInfo>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || !seenPackages.add(pkg)) continue
            val appInfo = info.activityInfo.applicationInfo
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            val category = resolveCategoryName(appInfo)
            val icon = try {
                info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            list.add(
                DeviceAppInfo(
                    packageName = pkg,
                    appName = label,
                    category = category,
                    usageMinutesToday = usageByPackage[pkg] ?: 0L,
                    icon = icon,
                )
            )
        }
        return list.sortedWith(
            compareByDescending<DeviceAppInfo> { it.usageMinutesToday }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun launchApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveCategoryName(appInfo: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "Games"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "Reading & News"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Learning & Tools"
                ApplicationInfo.CATEGORY_IMAGE -> "Creative"
                else -> "General"
            }
        }
        return "General"
    }
}
