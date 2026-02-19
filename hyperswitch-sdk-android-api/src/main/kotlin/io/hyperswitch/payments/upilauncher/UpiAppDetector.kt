package io.hyperswitch.payments.upilauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections


/**
 * Data class representing information about an installed UPI app
 */
data class UpiAppInfo(
    val packageName: String,
    val appName: String
)

object UpiAppDetector {
    /**
     * Queries the system for all apps that can handle UPI payment intents
     * @param context Android context
     * @return List of UpiAppInfo containing details of installed UPI apps
     */
    fun getInstalledUpiApps(context: Context): List<UpiAppInfo> {
        return try {
            val pm = context.getPackageManager()

//            val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            val upiIntent = Intent()
            upiIntent.setData(Uri.parse("upi://pay"))
            val packageManager = context.packageManager
            val resolveInfoList = packageManager.queryIntentActivities(upiIntent, 0)
//            val packages: MutableList<ApplicationInfo?>? =
//            packageManager.getInstalledApplications(PackageManager.MATCH_ALL)

            val launchables: MutableList<ResolveInfo>?
            launchables = pm.queryIntentActivities(upiIntent, 0)


            Log.d("upilist",launchables.toString())

//            resolveInfoList.map { resolveInfo ->
            launchables.map { resolveInfo ->
                UpiAppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    appName = resolveInfo.loadLabel(packageManager).toString()
                )
            }.distinctBy { it.packageName } // Remove duplicates if any
        } catch (e: Exception) {
            emptyList()
        }
    }
//    fun findApps(payload: String?): String {
//        val pm = context.getPackageManager()
//        val upi_apps = Intent()
//        upi_apps.setData(Uri.parse(payload))
//        val launchables: MutableList<ResolveInfo>?
//        launchables = pm.queryIntentActivities(upi_apps, 0)
//        Collections.sort<ResolveInfo?>(launchables, ResolveInfo.DisplayNameComparator(pm))
//
//        val apps = JSONArray()
//
//        for (resolveInfo in launchables) {
//            val jsonObject = JSONObject()
//            try {
//                val ai = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0)
//                jsonObject.put("packageName", ai.packageName)
//                jsonObject.put("appName", pm.getApplicationLabel(ai))
//
//                apps.put(jsonObject)
//            } catch (e: JSONException) {
//             Log.d("error",e.toString())
//            } catch (e: PackageManager.NameNotFoundException) {
//
//                Log.d("error",e.toString())
//            }
//        }
//
//        return apps.toString()
//    }
    /**
     * Checks if a specific package is installed and supports UPI
     * @param context Android context
     * @param packageName The package name to check
     * @return true if the app is installed and supports UPI, false otherwise
     */
    fun isUpiAppInstalled(context: Context, packageName: String): Boolean {
        return getInstalledUpiApps(context).any { it.packageName == packageName }
    }
}
